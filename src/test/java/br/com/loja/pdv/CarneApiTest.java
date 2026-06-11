package br.com.loja.pdv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recebimento de carnê: status de parcela é sempre CALCULADO por alocação
 * FIFO dos pagamentos — nada de flag gravada. Roda contra PostgreSQL real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CarneApiTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    Long clienteId;
    Long vendedorId;

    @BeforeEach
    void preparar() {
        jdbc.execute("""
                TRUNCATE item_venda, parcela_fiado, pagamento_fiado, venda,
                         variacao, produto, marca, vendedor, cliente
                RESTART IDENTITY CASCADE""");
        clienteId = jdbc.queryForObject(
                "INSERT INTO cliente (nome, cpf) VALUES ('Devedora Teste', '11122233344') RETURNING id", Long.class);
        vendedorId = jdbc.queryForObject(
                "INSERT INTO vendedor (nome) VALUES ('Rosana') RETURNING id", Long.class);
        // três parcelas migradas do SET: 100 (2023-01-10), 100 (2023-02-10), 50 (2023-03-10)
        jdbc.update("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data) VALUES
                (?, -100, 'DEBITO_INICIAL', '2023-01-10'),
                (?, -100, 'DEBITO_INICIAL', '2023-02-10'),
                (?,  -50, 'DEBITO_INICIAL', '2023-03-10')""", clienteId, clienteId, clienteId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> carne() {
        return http.getForEntity("/api/clientes/" + clienteId + "/carne", Map.class).getBody();
    }

    @Test
    void carneListaParcelasAbertasMaisAntigasPrimeiro() {
        var c = carne();
        assertThat(c.get("saldoDevedor")).isEqualTo(250.0);
        assertThat(c.get("parcelasAbertas")).isEqualTo(3);
        assertThat(c.get("vencimentoMaisAntigo")).isEqualTo("2023-01-10");
        var parcelas = (List<Map<String, Object>>) c.get("parcelas");
        assertThat(parcelas.get(0).get("vencimento")).isEqualTo("2023-01-10");
        assertThat((int) (long) ((Number) parcelas.get(0).get("diasAtraso")).longValue()).isGreaterThan(90);
    }

    @Test
    void recebimentoParcialCobreFifoEMostraDiferenca() {
        // paga 150: quita a 1ª (100), metade da 2ª (50 de 100), 3ª intacta
        var resp = http.postForEntity("/api/recebimentos",
                Map.of("clienteId", clienteId, "valor", "150.00", "tipo", "PIX", "vendedorId", vendedorId),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("saldoAnterior")).isEqualTo(250.0);
        assertThat(resp.getBody().get("saldoRestante")).isEqualTo(100.0);
        assertThat((List<?>) resp.getBody().get("parcelasQuitadas")).hasSize(1);
        var parcial = (Map<String, Object>) resp.getBody().get("parcelaParcial");
        assertThat(parcial.get("valorAberto")).isEqualTo(50.0);

        var c = carne();
        assertThat(c.get("parcelasAbertas")).isEqualTo(2); // 2ª (parcial) e 3ª
        var parcelas = (List<Map<String, Object>>) c.get("parcelas");
        assertThat(parcelas.get(0).get("valorAberto")).isEqualTo(50.0);
        assertThat(parcelas.get(0).get("valor")).isEqualTo(100.0);
    }

    @Test
    void receberMaisQueOSaldoERecusado() {
        var resp = http.postForEntity("/api/recebimentos",
                Map.of("clienteId", clienteId, "valor", "999.00", "tipo", "DINHEIRO", "vendedorId", vendedorId),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagamento_fiado WHERE valor > 0", Integer.class)).isZero();
    }

    @Test
    void tipoInvalidoERecusado() {
        var resp = http.postForEntity("/api/recebimentos",
                Map.of("clienteId", clienteId, "valor", "10.00", "tipo", "DEBITO_INICIAL", "vendedorId", vendedorId),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void entradaDeVendaFiadoNaoContaDuasVezesNoCarne() {
        // venda fiado de 200 com entrada de 50: parcelas líquidas 2x75
        var venda = Map.of(
                "clienteId", clienteId, "formaPagamento", "FIADO",
                "fiado", Map.of("entradaValor", "50.00", "entradaTipo", "DINHEIRO",
                        "parcelas", List.of(
                                Map.of("numero", 1, "valor", "75.00", "vencimento", "2030-01-10"),
                                Map.of("numero", 2, "valor", "75.00", "vencimento", "2030-02-10"))),
                "itens", List.of(Map.of("variacaoId", criarVariacao(), "quantidade", 1, "precoUnit", "200.00")));
        assertThat(http.postForEntity("/api/vendas", venda, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        var c = carne();
        // saldo: 250 legado + 200 venda - 50 entrada = 400
        assertThat(c.get("saldoDevedor")).isEqualTo(400.0);
        // parcelas abertas: 3 legadas + 2 da venda (entrada NÃO abate as legadas)
        assertThat(c.get("parcelasAbertas")).isEqualTo(5);
        var soma = ((List<Map<String, Object>>) c.get("parcelas")).stream()
                .mapToDouble(p -> ((Number) p.get("valorAberto")).doubleValue()).sum();
        assertThat(soma).isEqualTo(400.0); // visão FIFO bate com o saldo calculado
    }

    @Test
    void historicoMostraRecebimentoComFuncionario() {
        http.postForEntity("/api/recebimentos",
                Map.of("clienteId", clienteId, "valor", "100.00", "tipo", "DINHEIRO", "vendedorId", vendedorId),
                Map.class);
        var ultimos = (List<Map<String, Object>>) carne().get("ultimosPagamentos");
        assertThat(ultimos).hasSize(1);
        assertThat(ultimos.get(0).get("vendedorNome")).isEqualTo("Rosana");
    }

    private Long criarVariacao() {
        Long produtoId = jdbc.queryForObject(
                "INSERT INTO produto (codigo, nome, preco) VALUES ('T1', 'Tênis', 200) RETURNING id", Long.class);
        return jdbc.queryForObject(
                "INSERT INTO variacao (produto_id, estoque) VALUES (?, 5) RETURNING id", Long.class, produtoId);
    }
}
