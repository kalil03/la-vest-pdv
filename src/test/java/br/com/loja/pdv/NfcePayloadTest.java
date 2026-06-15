package br.com.loja.pdv;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.service.NfceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Montagem do payload da NFC-e (Focus NFe) — teste unitário puro, sem banco
 * nem SEFAZ. Valida a tradução Venda -> JSON fiscal.
 */
class NfcePayloadTest {

    private final NfceService nfce = new NfceService("102", "5102", "12345678000199");

    private Produto produto(String codigo, String nome, String ncm, String csosn) {
        Produto p = new Produto();
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setNcm(ncm);
        p.setUnidade("UN");
        p.setOrigem(0);
        p.setCsosn(csosn);
        Variacao v = new Variacao();
        p.adicionarVariacao(v);
        return p;
    }

    private ItemVenda item(Produto p, int qtd, String preco) {
        ItemVenda i = new ItemVenda();
        i.setVariacao(p.getVariacoes().get(0));
        i.setQuantidade(qtd);
        i.setPrecoUnit(new BigDecimal(preco));
        return i;
    }

    @Test
    @SuppressWarnings("unchecked")
    void vendaAVistaViraPayloadComItensFormaPagamentoETributacaoPadrao() {
        Produto tenis = produto("T100", "Tênis Runner", "64041900", null);
        Venda venda = new Venda();
        venda.setFormaPagamento(FormaPagamento.DINHEIRO);
        venda.adicionarItem(item(tenis, 2, "150.00"));
        venda.setDesconto(BigDecimal.ZERO);
        venda.setTotal(new BigDecimal("300.00"));

        var payload = nfce.montarPayload(venda);

        assertThat(payload.get("cnpj_emitente")).isEqualTo("12345678000199");
        assertThat(payload.get("consumidor_final")).isEqualTo("1");

        var itens = (List<Map<String, Object>>) payload.get("items");
        assertThat(itens).hasSize(1);
        var it = itens.get(0);
        assertThat(it.get("ncm")).isEqualTo("64041900");
        assertThat(it.get("cfop")).isEqualTo("5102");                 // padrão da loja
        assertThat(it.get("icms_situacao_tributaria")).isEqualTo("102"); // CSOSN padrão
        assertThat(it.get("valor_bruto")).isEqualTo("300.00");

        var pagtos = (List<Map<String, Object>>) payload.get("formas_pagamento");
        assertThat(pagtos.get(0).get("forma_pagamento")).isEqualTo("01"); // dinheiro
        assertThat(pagtos.get(0).get("valor_pagamento")).isEqualTo("300.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void descontoERateadoEntreOsItensFechandoComOTotal() {
        Produto a = produto("A", "Camiseta", "61091000", null);
        Produto b = produto("B", "Bermuda", "62034200", null);
        Venda venda = new Venda();
        venda.setFormaPagamento(FormaPagamento.PIX);
        venda.adicionarItem(item(a, 1, "100.00"));
        venda.adicionarItem(item(b, 1, "100.00"));
        venda.setDesconto(new BigDecimal("30.00"));
        venda.setTotal(new BigDecimal("170.00"));

        var payload = nfce.montarPayload(venda);
        var itens = (List<Map<String, Object>>) payload.get("items");

        // 30 de desconto dividido igual: 15 + 15, soma fecha
        BigDecimal somaDesc = itens.stream()
                .map(i -> new BigDecimal((String) i.get("valor_desconto")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(somaDesc).isEqualByComparingTo("30.00");
        assertThat(((List<Map<String, Object>>) payload.get("formas_pagamento"))
                .get(0).get("forma_pagamento")).isEqualTo("17"); // pix
    }

    @Test
    @SuppressWarnings("unchecked")
    void csosnProprioDoProdutoVenceOPadrao() {
        Produto p = produto("P", "Perfume", "33030010", "500"); // CSOSN próprio
        Venda venda = new Venda();
        venda.setFormaPagamento(FormaPagamento.CARTAO);
        venda.adicionarItem(item(p, 1, "200.00"));
        venda.setDesconto(BigDecimal.ZERO);
        venda.setTotal(new BigDecimal("200.00"));

        var itens = (List<Map<String, Object>>) nfce.montarPayload(venda).get("items");
        assertThat(itens.get(0).get("icms_situacao_tributaria")).isEqualTo("500");
    }

    @Test
    @SuppressWarnings("unchecked")
    void clienteComCpfViraDestinatarioNaNota() {
        Produto p = produto("P", "Bolsa", "42022200", null);
        Cliente c = new Cliente();
        c.setNome("Maria Souza");
        c.setCpf("11122233344");
        Venda venda = new Venda();
        venda.setCliente(c);
        venda.setFormaPagamento(FormaPagamento.FIADO);
        venda.adicionarItem(item(p, 1, "90.00"));
        venda.setDesconto(BigDecimal.ZERO);
        venda.setTotal(new BigDecimal("90.00"));

        var payload = nfce.montarPayload(venda);
        assertThat(payload.get("cpf_destinatario")).isEqualTo("11122233344");
        assertThat(payload.get("nome_destinatario")).isEqualTo("Maria Souza");
        assertThat(((List<Map<String, Object>>) payload.get("formas_pagamento"))
                .get(0).get("forma_pagamento")).isEqualTo("05"); // fiado = crédito loja
    }
}
