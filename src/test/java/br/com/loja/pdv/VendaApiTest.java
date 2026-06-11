package br.com.loja.pdv;

import br.com.loja.pdv.domain.Cliente;
import br.com.loja.pdv.domain.Produto;
import br.com.loja.pdv.domain.Variacao;
import br.com.loja.pdv.repository.ClienteRepository;
import br.com.loja.pdv.repository.ProdutoRepository;
import br.com.loja.pdv.repository.VariacaoRepository;
import br.com.loja.pdv.repository.VendaRepository;
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
 * Testes de integração do endpoint de fechar venda, cobrindo as regras de ouro:
 * atomicidade (tudo ou nada) e baixa de estoque na mesma transação.
 * Roda contra o banco pdv_test (PostgreSQL real, mesmas migrations do Flyway).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VendaApiTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProdutoRepository produtoRepository;
    @Autowired VariacaoRepository variacaoRepository;
    @Autowired VendaRepository vendaRepository;
    @Autowired ClienteRepository clienteRepository;

    Long variacaoTenis38;
    Long variacaoPerfume;

    @BeforeEach
    void limparEPopular() {
        jdbc.execute("""
                TRUNCATE item_venda, parcela_fiado, pagamento_fiado, venda,
                         variacao, produto, marca, vendedor, cliente
                RESTART IDENTITY CASCADE""");

        Produto tenis = new Produto();
        tenis.setCodigo("T100");
        tenis.setNome("Tênis Runner");
        tenis.setCategoria("Calçados");
        tenis.setPreco(new BigDecimal("150.00"));
        Variacao v38 = new Variacao();
        v38.setTamanho("38");
        v38.setEstoque(10);
        tenis.adicionarVariacao(v38);
        produtoRepository.save(tenis);
        variacaoTenis38 = v38.getId();

        Produto perfume = new Produto();
        perfume.setCodigo("P200");
        perfume.setNome("Perfume Floral");
        perfume.setCategoria("Perfumes");
        perfume.setPreco(new BigDecimal("80.00"));
        perfume.adicionarVariacao(new Variacao()); // variação "padrão", sem grade
        produtoRepository.save(perfume);
        variacaoPerfume = perfume.getVariacoes().get(0).getId();
    }

    @Test
    void vendaAVistaGravaVendaEBaixaEstoqueNaMesmaOperacao() {
        var req = Map.of(
                "formaPagamento", "DINHEIRO",
                "itens", List.of(
                        Map.of("variacaoId", variacaoTenis38, "quantidade", 2, "precoUnit", "150.00"),
                        Map.of("variacaoId", variacaoPerfume, "quantidade", 1, "precoUnit", "80.00")));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("total")).isEqualTo(380.00);
        assertThat(vendaRepository.count()).isEqualTo(1);
        assertThat(estoque(variacaoTenis38)).isEqualTo(8);   // 10 - 2
        assertThat(estoque(variacaoPerfume)).isEqualTo(-1);  // estoque negativo permitido por design
    }

    @Test
    void falhaEmUmItemDesfazAVendaInteira_atomicidade() {
        long variacaoInexistente = 99999L;
        var req = Map.of(
                "formaPagamento", "PIX",
                "itens", List.of(
                        Map.of("variacaoId", variacaoTenis38, "quantidade", 3),
                        Map.of("variacaoId", variacaoInexistente, "quantidade", 1)));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Nada pode ter sobrado: nem venda, nem baixa do primeiro item (rollback total)
        assertThat(vendaRepository.count()).isZero();
        assertThat(estoque(variacaoTenis38)).isEqualTo(10);
    }

    @Test
    void fiadoSemClienteERecusado() {
        var req = Map.of(
                "formaPagamento", "FIADO",
                "itens", List.of(Map.of("variacaoId", variacaoTenis38, "quantidade", 1)));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(vendaRepository.count()).isZero();
        assertThat(estoque(variacaoTenis38)).isEqualTo(10);
    }

    @Test
    void fiadoComClienteNovoCriaClienteELancaNoCarne() {
        var req = Map.of(
                "clienteNome", "Maria da Silva",
                "formaPagamento", "FIADO",
                "itens", List.of(Map.of("variacaoId", variacaoTenis38, "quantidade", 1, "precoUnit", "150.00")));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("clienteNome")).isEqualTo("Maria da Silva");
        // A dívida é calculada (venda FIADO - pagamentos), nunca armazenada
        assertThat(resp.getBody().get("saldoDevedor")).isEqualTo(150.00);

        Cliente maria = clienteRepository.buscar("Maria").get(0);
        assertThat(clienteRepository.saldoDevedor(maria.getId())).isEqualByComparingTo("150.00");
        assertThat(estoque(variacaoTenis38)).isEqualTo(9);
    }

    @Test
    void descontoReduzOTotalEFicaRegistrado() {
        var req = Map.of(
                "formaPagamento", "PIX",
                "desconto", "30.00",
                "itens", List.of(Map.of("variacaoId", variacaoTenis38, "quantidade", 2, "precoUnit", "150.00")));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("subtotal")).isEqualTo(300.00);
        assertThat(resp.getBody().get("desconto")).isEqualTo(30.00);
        assertThat(resp.getBody().get("total")).isEqualTo(270.00);
    }

    @Test
    void descontoMaiorQueAVendaERecusado() {
        var req = Map.of(
                "formaPagamento", "PIX",
                "desconto", "500.00",
                "itens", List.of(Map.of("variacaoId", variacaoTenis38, "quantidade", 1, "precoUnit", "150.00")));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(vendaRepository.count()).isZero();
    }

    @Test
    void fiadoParceladoComEntradaCriaParcelasEPagamentoDaEntrada() {
        // Tênis R$ 150 x 2 = 300; entrada de 60 em dinheiro; 3x de 80
        var req = Map.of(
                "clienteNome", "Carlos Souza",
                "formaPagamento", "FIADO",
                "fiado", Map.of(
                        "entradaValor", "60.00",
                        "entradaTipo", "DINHEIRO",
                        "parcelas", List.of(
                                Map.of("numero", 1, "valor", "80.00", "vencimento", "2026-07-11"),
                                Map.of("numero", 2, "valor", "80.00", "vencimento", "2026-08-11"),
                                Map.of("numero", 3, "valor", "80.00", "vencimento", "2026-09-11"))),
                "itens", List.of(Map.of("variacaoId", variacaoTenis38, "quantidade", 2, "precoUnit", "150.00")));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("entrada")).isEqualTo(60.00);
        // Saldo devedor = venda 300 - entrada 60 (a entrada é um pagamento normal do carnê)
        assertThat(resp.getBody().get("saldoDevedor")).isEqualTo(240.00);
        assertThat((List<?>) resp.getBody().get("parcelas")).hasSize(3);

        Integer parcelas = jdbc.queryForObject("SELECT COUNT(*) FROM parcela_fiado", Integer.class);
        assertThat(parcelas).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT SUM(valor) FROM pagamento_fiado", java.math.BigDecimal.class))
                .isEqualByComparingTo("60.00");
    }

    @Test
    void parcelasQueNaoFechamComOTotalDerrubamAVendaInteira() {
        var req = Map.of(
                "clienteNome", "Ana Lima",
                "formaPagamento", "FIADO",
                "fiado", Map.of(
                        "parcelas", List.of(
                                Map.of("numero", 1, "valor", "100.00", "vencimento", "2026-07-11"))),
                "itens", List.of(Map.of("variacaoId", variacaoTenis38, "quantidade", 1, "precoUnit", "150.00")));

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // rollback total: nem venda, nem parcela, nem cliente afetados
        assertThat(vendaRepository.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcela_fiado", Integer.class)).isZero();
        assertThat(estoque(variacaoTenis38)).isEqualTo(10);
    }

    @Test
    void vendaSemItensERecusada() {
        var req = Map.of("formaPagamento", "DINHEIRO", "itens", List.of());

        ResponseEntity<Map> resp = http.postForEntity("/api/vendas", req, Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(vendaRepository.count()).isZero();
    }

    private int estoque(Long variacaoId) {
        return jdbc.queryForObject("SELECT estoque FROM variacao WHERE id = ?", Integer.class, variacaoId);
    }
}
