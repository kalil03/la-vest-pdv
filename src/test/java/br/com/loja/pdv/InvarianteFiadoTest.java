package br.com.loja.pdv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rede de segurança do crediário: o invariante global
 * SUM(valor_aberto das parcelas mostradas no carnê) == saldo devedor calculado
 * é verificado APÓS CADA operação que mexe em dinheiro — venda fiado (com
 * entrada), recebimento parcial, estorno, baixa por incobrabilidade e
 * restauração. Como saldo e parcelas vêm da MESMA resposta da API, um filtro
 * de venda cancelada esquecido em qualquer query dos dois lados quebra aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InvarianteFiadoTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    Long clienteId;
    Long vendedorId;
    Long variacaoId;

    @BeforeEach
    void preparar() {
        jdbc.execute("""
                TRUNCATE baixa_fiado_item, baixa_fiado, item_venda, parcela_fiado,
                         pagamento_fiado, estorno, venda, variacao, produto, marca,
                         vendedor, cliente
                RESTART IDENTITY CASCADE""");
        clienteId = jdbc.queryForObject(
                "INSERT INTO cliente (nome) VALUES ('Cliente Invariante') RETURNING id", Long.class);
        vendedorId = jdbc.queryForObject(
                "INSERT INTO vendedor (nome) VALUES ('Rosana') RETURNING id", Long.class);
        Long produtoId = jdbc.queryForObject(
                "INSERT INTO produto (codigo, nome, preco) VALUES ('INV1', 'Tênis', 100) RETURNING id",
                Long.class);
        variacaoId = jdbc.queryForObject(
                "INSERT INTO variacao (produto_id, estoque) VALUES (?, 50) RETURNING id",
                Long.class, produtoId);
        // uma parcela legada do SET no meio, para o invariante cobrir as duas origens
        jdbc.update("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data, valor_aberto)
                VALUES (?, -40, 'DEBITO_INICIAL', CAST('2023-01-10' AS date), 40)""", clienteId);
    }

    @Test
    void invarianteSobreviveAVendaRecebimentoEstornoBaixaERestauracao() {
        invarianteOk(40.0); // só o débito legado

        // 1) venda fiado de 150 com entrada de 50 (2 parcelas: 60 + 40)
        Long venda1 = vendaFiado("150.00", "50.00",
                List.of(Map.of("numero", 1, "valor", "60.00", "vencimento", "2030-01-10"),
                        Map.of("numero", 2, "valor", "40.00", "vencimento", "2030-02-10")));
        invarianteOk(140.0); // 40 legado + 100 da venda

        // 2) recebimento parcial de 30 na primeira parcela da venda
        Long parcela1 = jdbc.queryForObject(
                "SELECT id FROM parcela_fiado WHERE venda_id = ? AND numero = 1", Long.class, venda1);
        var receb = http.postForEntity("/api/recebimentos", Map.of(
                "clienteId", clienteId, "valor", "30.00", "tipo", "PIX", "vendedorId", vendedorId,
                "alocacoes", List.of(Map.of("parcelaId", "V" + parcela1, "valor", "30.00"))), Map.class);
        assertThat(receb.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        invarianteOk(110.0);

        // 3) segunda venda fiado de 80, depois estornada: os dois lados caem juntos
        Long venda2 = vendaFiado("80.00", null,
                List.of(Map.of("numero", 1, "valor", "80.00", "vencimento", "2030-03-10")));
        invarianteOk(190.0);
        http.delete("/api/vendas/" + venda2 + "?operador=Teste&motivo=estorno");
        invarianteOk(110.0);

        // 4) baixa por incobrabilidade: zera tudo, saldo e parcelas juntos
        var baixa = http.postForEntity("/api/baixas",
                Map.of("clienteId", clienteId, "motivo", "teste", "operador", "Teste"), Map.class);
        assertThat(baixa.getStatusCode()).isEqualTo(HttpStatus.OK);
        invarianteOk(0.0);

        // 5) restaurar a baixa: tudo volta idêntico
        Long baixaId = ((Number) baixa.getBody().get("id")).longValue();
        http.postForEntity("/api/baixas/" + baixaId + "/restaurar?operador=Teste", null, Map.class);
        invarianteOk(110.0);
    }

    private Long vendaFiado(String precoUnit, String entrada, List<Map<String, Object>> parcelas) {
        var fiado = new java.util.HashMap<String, Object>();
        if (entrada != null) { fiado.put("entradaValor", entrada); fiado.put("entradaTipo", "PIX"); }
        fiado.put("parcelas", parcelas);
        var resp = http.postForEntity("/api/vendas", Map.of(
                "clienteId", clienteId, "vendedorId", vendedorId, "formaPagamento", "FIADO",
                "tipoNotinha", "Geral", "fiado", fiado,
                "itens", List.of(Map.of("variacaoId", variacaoId, "quantidade", 1, "precoUnit", precoUnit))),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) resp.getBody().get("id")).longValue();
    }

    /** saldo e parcelas vêm da MESMA resposta: a igualdade é o invariante. */
    @SuppressWarnings("unchecked")
    private void invarianteOk(double saldoEsperado) {
        Map<String, Object> carne = http.getForEntity(
                "/api/clientes/" + clienteId + "/carne", Map.class).getBody();
        double saldo = ((Number) carne.get("saldoDevedor")).doubleValue();
        double somaAbertas = ((List<Map<String, Object>>) carne.get("parcelas")).stream()
                .mapToDouble(p -> ((Number) p.get("valorAberto")).doubleValue()).sum();

        assertThat(saldo).isEqualTo(saldoEsperado);
        assertThat(somaAbertas)
                .as("invariante SUM(valor_aberto) == saldo devedor")
                .isEqualTo(saldo);
    }
}
