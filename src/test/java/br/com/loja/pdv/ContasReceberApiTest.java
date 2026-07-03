package br.com.loja.pdv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Contas a receber: visão única das parcelas com status calculado. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// os testes leem o JSON da API como Map generico DE PROPOSITO (estilo da suite):
// um cast que nao bata com a resposta quebra o teste, que e o comportamento desejado
@SuppressWarnings({ "rawtypes", "unchecked" })
class ContasReceberApiTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    Long clienteId;

    @BeforeEach
    void preparar() {
        jdbc.execute("""
                TRUNCATE item_venda, parcela_fiado, pagamento_fiado, venda,
                         variacao, produto, marca, vendedor, cliente
                RESTART IDENTITY CASCADE""");
        clienteId = jdbc.queryForObject(
                "INSERT INTO cliente (nome) VALUES ('Devedora Teste') RETURNING id", Long.class);
        // atrasada (aberta, vencimento passado), quitada, e parcial
        jdbc.update("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data, valor_aberto) VALUES
                (?, -100, 'DEBITO_INICIAL', '2023-01-10', 100),
                (?, -80,  'DEBITO_INICIAL', '2023-02-10', 0),
                (?, -60,  'DEBITO_INICIAL', '2023-03-10', 25)""", clienteId, clienteId, clienteId);
    }

    private Map<String, Object> listar(String query) {
        return http.getForEntity("/api/contas-receber" + query, Map.class).getBody();
    }

    @Test
    void listaTodasComStatusCalculado() {
        var r = listar("");
        assertThat(r.get("total")).isEqualTo(3);
        var contas = (List<Map<String, Object>>) r.get("contas");
        assertThat(contas).extracting(c -> c.get("status"))
                .containsExactly("ATRASADA", "QUITADA", "ATRASADA"); // parcial vencida = atrasada
        var totais = (Map<String, Object>) r.get("totais");
        assertThat(totais.get("totalAberto")).isEqualTo(125.0);   // 100 + 25
        assertThat(totais.get("totalVencido")).isEqualTo(125.0);  // tudo vencido
        assertThat(totais.get("parcelasAbertas")).isEqualTo(2);
    }

    @Test
    void filtraPorStatusEBusca() {
        assertThat(listar("?status=QUITADA").get("total")).isEqualTo(1);
        assertThat(listar("?status=PARCIAL").get("total")).isEqualTo(1);
        assertThat(listar("?q=devedora").get("total")).isEqualTo(3);
        assertThat(listar("?q=ninguem").get("total")).isEqualTo(0);
        assertThat(listar("?de=2023-02-01&ate=2023-02-28").get("total")).isEqualTo(1);
    }

    @Test
    void parcelaDeVendaApareceComNumeroDaNotinha() {
        Long vendedorId = jdbc.queryForObject("INSERT INTO vendedor (nome) VALUES ('R') RETURNING id", Long.class);
        Long produtoId = jdbc.queryForObject(
                "INSERT INTO produto (codigo, nome, preco) VALUES ('T1', 'Tênis', 200) RETURNING id", Long.class);
        Long variacaoId = jdbc.queryForObject(
                "INSERT INTO variacao (produto_id, estoque) VALUES (?, 5) RETURNING id", Long.class, produtoId);
        var venda = Map.of(
                "clienteId", clienteId, "vendedorId", vendedorId, "formaPagamento", "FIADO", "tipoNotinha", "Geral",
                "fiado", Map.of("parcelas", List.of(
                        Map.of("numero", 1, "valor", "200.00", "vencimento", "2030-01-10"))),
                "itens", List.of(Map.of("variacaoId", variacaoId, "quantidade", 1, "precoUnit", "200.00")));
        http.postForEntity("/api/vendas", venda, Map.class);

        var contas = (List<Map<String, Object>>) listar("?status=ABERTA").get("contas");
        var daVenda = contas.stream().filter(c -> c.get("notinha") != null).findFirst().orElseThrow();
        assertThat(daVenda.get("descricao")).isEqualTo("Parcela 1/1 · Geral");
        assertThat(daVenda.get("status")).isEqualTo("ABERTA"); // vence em 2030
    }
}
