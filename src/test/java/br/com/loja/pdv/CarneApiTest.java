package br.com.loja.pdv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recebimento de carnê com rateio POR ORDEM DE SELEÇÃO: a tela manda quanto
 * abater de cada parcela. O saldo devedor continua sempre calculado;
 * valor_aberto é só o rateio (invariante: SUM(valor_aberto) == saldo).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CarneApiTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    Long clienteId;
    Long vendedorId;
    Long parcela100Antiga;  // 2023-01-10
    Long parcela100Media;   // 2023-02-10
    Long parcela50Nova;     // 2023-03-10

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
        parcela100Antiga = debito("2023-01-10", 100);
        parcela100Media = debito("2023-02-10", 100);
        parcela50Nova = debito("2023-03-10", 50);
    }

    private Long debito(String venc, int valor) {
        return jdbc.queryForObject("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data, valor_aberto)
                VALUES (?, ?, 'DEBITO_INICIAL', CAST(? AS date), ?) RETURNING id""",
                Long.class, clienteId, -valor, venc, valor);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> carne() {
        return http.getForEntity("/api/clientes/" + clienteId + "/carne", Map.class).getBody();
    }

    private ResponseEntity<Map> receber(String valor, List<Map<String, Object>> alocacoes) {
        return http.postForEntity("/api/recebimentos", Map.of(
                "clienteId", clienteId, "valor", valor, "tipo", "PIX",
                "vendedorId", vendedorId, "alocacoes", alocacoes), Map.class);
    }

    @Test
    void carneListaParcelasAbertasMaisAntigasPrimeiro() {
        var c = carne();
        assertThat(c.get("saldoDevedor")).isEqualTo(250.0);
        assertThat(c.get("parcelasAbertas")).isEqualTo(3);
        assertThat(c.get("vencimentoMaisAntigo")).isEqualTo("2023-01-10");
        var parcelas = (List<Map<String, Object>>) c.get("parcelas");
        assertThat(parcelas.get(0).get("vencimento")).isEqualTo("2023-01-10");
        assertThat(((Number) parcelas.get(0).get("diasAtraso")).longValue()).isGreaterThan(90);
    }

    @Test
    void recebimentoSegueOrdemDeSelecaoComValorLimite() {
        // a moça selecionou a de 50 PRIMEIRO e depois a antiga de 100,
        // mas só tinha 80: quita a de 50 e abate 30 da antiga
        var resp = receber("80.00", List.of(
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00"),
                Map.of("parcelaId", "L" + parcela100Antiga, "valor", "30.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("saldoAnterior")).isEqualTo(250.0);
        assertThat(resp.getBody().get("saldoRestante")).isEqualTo(170.0);
        var itens = (List<Map<String, Object>>) resp.getBody().get("itens");
        assertThat(itens).hasSize(2);
        assertThat(itens.get(0).get("restante")).isEqualTo(0.0);   // 50 quitada
        assertThat(itens.get(1).get("restante")).isEqualTo(70.0);  // 100 - 30
        // comprovante do pagamento parcial: o valor ORIGINAL da nota sai no papel
        assertThat(itens.get(0).get("valorOriginal")).isEqualTo(50.0);
        assertThat(itens.get(1).get("valorOriginal")).isEqualTo(100.0);

        var c = carne();
        assertThat(c.get("parcelasAbertas")).isEqualTo(2);
        var parcelas = (List<Map<String, Object>>) c.get("parcelas");
        assertThat(parcelas.get(0).get("valorAberto")).isEqualTo(70.0);
        // invariante: rateio fecha com o saldo calculado
        var soma = parcelas.stream().mapToDouble(p -> ((Number) p.get("valorAberto")).doubleValue()).sum();
        assertThat(soma).isEqualTo(170.0);
    }

    @Test
    void selecionarUmaParcelaSozinhaMesmoNaoSendoAMaisAntiga() {
        var resp = receber("50.00", List.of(
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var c = carne();
        assertThat(c.get("saldoDevedor")).isEqualTo(200.0);
        var parcelas = (List<Map<String, Object>>) c.get("parcelas");
        // as duas antigas continuam intactas; a nova sumiu
        assertThat(parcelas).hasSize(2);
        assertThat(parcelas).allSatisfy(p -> assertThat(p.get("valorAberto")).isEqualTo(100.0));
    }

    @Test
    void somaDasAlocacoesTemQueFecharComOValor() {
        var resp = receber("80.00", List.of(
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // rollback total: nada abatido, nenhum pagamento gravado
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagamento_fiado WHERE valor > 0", Integer.class)).isZero();
        assertThat(carne().get("saldoDevedor")).isEqualTo(250.0);
    }

    @Test
    void naoDeixaAbaterMaisQueORestanteDaParcela() {
        var resp = receber("60.00", List.of(
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "60.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(carne().get("saldoDevedor")).isEqualTo(250.0);
    }

    @Test
    void mesmaParcelaRepetidaNoRecebimentoERecusada() {
        // sem esse bloqueio, dois UPDATEs atômicos válidos abateriam em dobro
        var resp = receber("100.00", List.of(
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00"),
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagamento_fiado WHERE valor > 0", Integer.class)).isZero();
        assertThat(carne().get("saldoDevedor")).isEqualTo(250.0);
    }

    @Test
    void falhaNaSegundaParcelaDesfazOAbateDaPrimeira() {
        // outra operação quitou a de 50 (abate + pagamento, invariante intacto)...
        jdbc.update("UPDATE pagamento_fiado SET valor_aberto = 0 WHERE id = ?", parcela50Nova);
        jdbc.update("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo) VALUES (?, 50, 'DINHEIRO')""",
                clienteId);

        // ...mas a tela da atendente ainda a mostrava aberta: parcela 1 abate
        // com sucesso e a 2 falha — o rollback tem que desfazer TUDO
        var resp = receber("80.00", List.of(
                Map.of("parcelaId", "L" + parcela100Antiga, "valor", "30.00"),
                Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT valor_aberto FROM pagamento_fiado WHERE id = ?",
                BigDecimal.class, parcela100Antiga)).isEqualByComparingTo("100"); // rollback do abate
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagamento_fiado WHERE valor > 0", Integer.class))
                .isEqualTo(1); // só o pagamento da outra operação
        assertThat(carne().get("saldoDevedor")).isEqualTo(200.0);
        // invariante: SUM(valor_aberto) == saldo
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(valor_aberto), 0) FROM pagamento_fiado
                WHERE tipo = 'DEBITO_INICIAL'""", BigDecimal.class))
                .isEqualByComparingTo("200");
    }

    @Test
    void recebimentoConcorrenteNaMesmaParcelaSoUmPassa() throws Exception {
        // duas atendentes recebem a MESMA parcela de 100 ao mesmo tempo: o
        // UPDATE condicional deixa exatamente uma passar; a outra leva 400.
        // Sem ele, as duas passariam e a dívida cairia 200 por 100 recebidos.
        java.util.concurrent.CountDownLatch pronto = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<HttpStatus> tentativa = () -> {
            pronto.await();
            return (HttpStatus) receber("100.00", List.of(
                    Map.of("parcelaId", "L" + parcela100Antiga, "valor", "100.00"))).getStatusCode();
        };
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<HttpStatus> f1 = pool.submit(tentativa);
            java.util.concurrent.Future<HttpStatus> f2 = pool.submit(tentativa);
            pronto.countDown();
            List<HttpStatus> statuses = List.of(f1.get(), f2.get());

            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.BAD_REQUEST);
        } finally {
            pool.shutdown();
        }
        assertThat(jdbc.queryForObject("SELECT valor_aberto FROM pagamento_fiado WHERE id = ?",
                BigDecimal.class, parcela100Antiga)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pagamento_fiado WHERE valor > 0", Integer.class)).isEqualTo(1);
        // invariante: 250 de dívida - 100 recebidos = 150, e o rateio fecha
        assertThat(carne().get("saldoDevedor")).isEqualTo(150.0);
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(valor_aberto), 0) FROM pagamento_fiado
                WHERE tipo = 'DEBITO_INICIAL'""", BigDecimal.class))
                .isEqualByComparingTo("150");
    }

    @Test
    void parcelaDeOutroClienteERecusada() {
        Long outroCliente = jdbc.queryForObject(
                "INSERT INTO cliente (nome) VALUES ('Outra Pessoa') RETURNING id", Long.class);
        Long parcelaDela = jdbc.queryForObject("""
                INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data, valor_aberto)
                VALUES (?, -40, 'DEBITO_INICIAL', CAST('2023-05-01' AS date), 40) RETURNING id""",
                Long.class, outroCliente);

        var resp = receber("40.00", List.of(
                Map.of("parcelaId", "L" + parcelaDela, "valor", "40.00")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void historicoMostraRecebimentoComFuncionarioEDetalhe() {
        receber("50.00", List.of(Map.of("parcelaId", "L" + parcela50Nova, "valor", "50.00")));

        var ultimos = (List<Map<String, Object>>) carne().get("ultimosPagamentos");
        assertThat(ultimos).hasSize(1);
        assertThat(ultimos.get(0).get("vendedorNome")).isEqualTo("Rosana");
        assertThat((String) ultimos.get(0).get("detalhe")).contains("Carnê SET");
    }

    @Test
    void observacaoDaVendaApareceNaParcelaDoCarne() {
        Long produtoId = jdbc.queryForObject(
                "INSERT INTO produto (codigo, nome, preco) VALUES ('T1', 'Tênis', 200) RETURNING id", Long.class);
        Long variacaoId = jdbc.queryForObject(
                "INSERT INTO variacao (produto_id, estoque) VALUES (?, 5) RETURNING id", Long.class, produtoId);
        var venda = Map.of(
                "clienteId", clienteId, "vendedorId", vendedorId, "formaPagamento", "FIADO", "tipoNotinha", "Geral",
                "observacao", "comprou no nome da avó",
                "fiado", Map.of("parcelas", List.of(
                        Map.of("numero", 1, "valor", "200.00", "vencimento", "2030-01-10"))),
                "itens", List.of(Map.of("variacaoId", variacaoId, "quantidade", 1, "precoUnit", "200.00")));
        assertThat(http.postForEntity("/api/vendas", venda, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        var parcelas = (List<Map<String, Object>>) carne().get("parcelas");
        var daVenda = parcelas.stream().filter(p -> p.get("notinha") != null).findFirst().orElseThrow();
        assertThat(daVenda.get("observacao")).isEqualTo("comprou no nome da avó");
        assertThat(((Number) daVenda.get("diasAtraso")).longValue()).isLessThan(0); // ainda vai vencer
    }

    @Test
    void buscaClientePorNumeroDaNotinha() {
        Long produtoId = jdbc.queryForObject(
                "INSERT INTO produto (codigo, nome, preco) VALUES ('T2', 'Bolsa', 90) RETURNING id", Long.class);
        Long variacaoId = jdbc.queryForObject(
                "INSERT INTO variacao (produto_id, estoque) VALUES (?, 5) RETURNING id", Long.class, produtoId);
        var venda = Map.of(
                "clienteId", clienteId, "vendedorId", vendedorId, "formaPagamento", "FIADO", "tipoNotinha", "Geral",
                "itens", List.of(Map.of("variacaoId", variacaoId, "quantidade", 1, "precoUnit", "90.00")));
        Long vendaId = ((Number) http.postForEntity("/api/vendas", venda, Map.class)
                .getBody().get("id")).longValue();

        ResponseEntity<Map> resp = http.getForEntity("/api/clientes/por-venda/" + vendaId, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("nome")).isEqualTo("Devedora Teste");
    }

    @Test
    void loginValidaCredenciais() {
        assertThat(http.postForEntity("/api/login",
                Map.of("login", "admin", "senha", "admin"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.postForEntity("/api/login",
                Map.of("login", "admin", "senha", "errada"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
