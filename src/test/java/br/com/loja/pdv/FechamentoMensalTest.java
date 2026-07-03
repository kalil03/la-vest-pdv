package br.com.loja.pdv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fechamento mensal: um mês com venda à vista, a prazo, Tênis/Geral, vendedores
 * diferentes, venda legada sem tipo e sem vendedor, retirada, recebimento de
 * carnê, entrada de fiado — e uma venda CANCELADA + uma de OUTRO mês, que não
 * podem contar em NENHUMA linha.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FechamentoMensalTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    Long anaId;
    Long biaId;
    Long clienteId;
    Long variacaoId;

    @BeforeEach
    void preparar() {
        jdbc.execute("""
                TRUNCATE item_venda, parcela_fiado, pagamento_fiado, venda,
                         variacao, produto, marca, vendedor, cliente, estorno,
                         fechamento_caixa, retirada_caixa
                RESTART IDENTITY CASCADE""");
        anaId = jdbc.queryForObject("INSERT INTO vendedor (nome) VALUES ('Ana') RETURNING id", Long.class);
        biaId = jdbc.queryForObject("INSERT INTO vendedor (nome) VALUES ('Bia') RETURNING id", Long.class);
        clienteId = jdbc.queryForObject(
                "INSERT INTO cliente (nome) VALUES ('Cliente Mês') RETURNING id", Long.class);
        Long produtoId = jdbc.queryForObject(
                "INSERT INTO produto (codigo, nome, preco) VALUES ('FM1', 'Tênis', 100) RETURNING id", Long.class);
        variacaoId = jdbc.queryForObject(
                "INSERT INTO variacao (produto_id, estoque) VALUES (?, 99) RETURNING id", Long.class, produtoId);
    }

    private Long venda(Long vendedorId, String forma, String tipo, String preco, Map<String, Object> fiado) {
        var body = new java.util.HashMap<String, Object>();
        body.put("vendedorId", vendedorId);
        body.put("formaPagamento", forma);
        body.put("tipoNotinha", tipo);
        if ("FIADO".equals(forma)) body.put("clienteId", clienteId);
        if (fiado != null) body.put("fiado", fiado);
        body.put("itens", List.of(Map.of("variacaoId", variacaoId, "quantidade", 1, "precoUnit", preco)));
        var resp = http.postForEntity("/api/vendas", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void fechamentoCruzaOsEixosEIgnoraCanceladaEOutroMes() {
        // à vista Tênis da Ana (100) + a prazo Geral da Bia (200, entrada 50 + parcela 150)
        venda(anaId, "DINHEIRO", "Tênis", "100.00", null);
        venda(biaId, "FIADO", "Geral", "200.00", Map.of(
                "entradaValor", "50.00", "entradaTipo", "PIX",
                "parcelas", List.of(Map.of("numero", 1, "valor", "150.00", "vencimento", "2030-01-10"))));
        // venda legada: sem vendedor e sem tipo (anterior às obrigatoriedades)
        jdbc.update("INSERT INTO venda (forma_pagamento, total, desconto) VALUES ('DINHEIRO', 50, 0)");
        // venda de OUTRO mês: não pode aparecer
        jdbc.update("INSERT INTO venda (forma_pagamento, total, desconto, data, tipo_notinha) "
                + "VALUES ('DINHEIRO', 777, 0, now() - interval '40 days', 'Tênis')");
        // venda CANCELADA: não pode contar em nenhuma linha
        Long cancelada = venda(anaId, "DINHEIRO", "Tênis", "999.00", null);
        http.delete("/api/vendas/" + cancelada + "?operador=Teste&motivo=estorno");
        // retirada (sangria) de 40
        http.postForEntity("/api/vendas/caixa-dia/retirada",
                Map.of("valor", "40.00", "motivo", "teste"), Map.class);
        // recebimento de carnê no balcão: 30 numa parcela migrada do SET
        Long debito = jdbc.queryForObject("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data, valor_aberto)
                VALUES (?, -30, 'DEBITO_INICIAL', now(), 30) RETURNING id""", Long.class, clienteId);
        assertThat(http.postForEntity("/api/recebimentos", Map.of(
                "clienteId", clienteId, "valor", "30.00", "tipo", "PIX", "vendedorId", anaId,
                "alocacoes", List.of(Map.of("parcelaId", "L" + debito, "valor", "30.00"))), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        LocalDate hoje = LocalDate.now(br.com.loja.pdv.Fuso.LOJA);
        Map<String, Object> f = http.getForEntity(
                "/api/fechamento-mensal?ano=" + hoje.getYear() + "&mes=" + hoje.getMonthValue(),
                Map.class).getBody();

        // categorias: Geral 200, Tênis 100 (a cancelada de 999 NÃO conta), Sem tipo 50
        var cats = (List<Map<String, Object>>) f.get("porCategoria");
        assertThat(cats).extracting(c -> c.get("categoria") + "=" + ((Number) c.get("total")).doubleValue())
                .containsExactly("Geral=200.0", "Tênis=100.0", "Sem tipo=50.0");
        assertThat(((Number) f.get("totalGeral")).doubleValue()).isEqualTo(350.0);
        assertThat(((Number) f.get("qtdVendas")).longValue()).isEqualTo(3);

        // vendedor × forma
        var vends = (List<Map<String, Object>>) f.get("porVendedor");
        Map<String, Map<String, Object>> porNome = new java.util.HashMap<>();
        vends.forEach(v -> porNome.put((String) v.get("vendedor"), v));
        assertThat(porNome).containsOnlyKeys("Ana", "Bia", "Sem vendedor");
        assertThat(((Number) porNome.get("Ana").get("aVista")).doubleValue()).isEqualTo(100.0);
        assertThat(((Number) porNome.get("Ana").get("aPrazo")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) porNome.get("Bia").get("aPrazo")).doubleValue()).isEqualTo(200.0);
        assertThat(((Number) porNome.get("Bia").get("aVista")).doubleValue()).isEqualTo(0.0);
        assertThat(((Number) porNome.get("Sem vendedor").get("total")).doubleValue()).isEqualTo(50.0);

        // recebimento (carnê balcão) ≠ entrada de fiado ≠ retirada
        assertThat(((Number) ((Map<String, Object>) f.get("recebimentoMes")).get("total")).doubleValue())
                .isEqualTo(30.0);
        assertThat(((Number) ((Map<String, Object>) f.get("entradasFiado")).get("total")).doubleValue())
                .isEqualTo(50.0);
        assertThat(((Number) ((Map<String, Object>) f.get("retiradas")).get("total")).doubleValue())
                .isEqualTo(40.0);
    }
}
