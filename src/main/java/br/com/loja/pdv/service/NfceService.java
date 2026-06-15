package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Monta o payload da NFC-e no formato da Focus NFe a partir de uma Venda.
 * Esta classe NÃO fala com a SEFAZ — só traduz a venda para o JSON fiscal.
 * A chamada HTTP entra quando o token da Focus for configurado.
 *
 * Tributação: o produto pode trazer CSOSN/CFOP próprios; sem isso, vale o
 * padrão da loja (Simples Nacional). O desconto da venda é rateado entre os
 * itens para o total da nota fechar com o que foi cobrado.
 */
@Service
public class NfceService {

    // PIS/COFINS fora do regime cumulativo — comum no Simples (confirmar c/ contador)
    private static final String PIS_COFINS_SIMPLES = "49";

    private final String csosnPadrao;
    private final String cfopPadrao;
    private final String cnpjEmitente;

    public NfceService(@Value("${fiscal.csosn-padrao:102}") String csosnPadrao,
                       @Value("${fiscal.cfop-padrao:5102}") String cfopPadrao,
                       @Value("${fiscal.cnpj:}") String cnpjEmitente) {
        this.csosnPadrao = csosnPadrao;
        this.cfopPadrao = cfopPadrao;
        this.cnpjEmitente = cnpjEmitente;
    }

    /** SEFAZ: forma de pagamento (tag tPag) a partir da nossa forma da venda. */
    private static String formaPagamentoSefaz(FormaPagamento f) {
        return switch (f) {
            case DINHEIRO -> "01";
            case PIX -> "17";
            case CARTAO -> "99"; // genérico; sem distinguir crédito/débito
            case FIADO -> "05";  // crédito loja (crediário próprio)
        };
    }

    public Map<String, Object> montarPayload(Venda venda) {
        List<ItemVenda> itens = venda.getItens();
        BigDecimal subtotal = itens.stream()
                .map(ItemVenda::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desconto = venda.getDesconto() != null ? venda.getDesconto() : BigDecimal.ZERO;

        List<Map<String, Object>> itensPayload = new ArrayList<>();
        BigDecimal descontoAplicado = BigDecimal.ZERO;
        for (int i = 0; i < itens.size(); i++) {
            ItemVenda item = itens.get(i);
            // rateia o desconto proporcional ao item; o último fecha o resto
            BigDecimal descItem;
            if (desconto.signum() == 0) {
                descItem = BigDecimal.ZERO;
            } else if (i == itens.size() - 1) {
                descItem = desconto.subtract(descontoAplicado);
            } else {
                descItem = desconto.multiply(item.getSubtotal())
                        .divide(subtotal, 2, RoundingMode.HALF_UP);
                descontoAplicado = descontoAplicado.add(descItem);
            }
            itensPayload.add(itemPayload(i + 1, item, descItem));
        }

        Map<String, Object> nfce = new LinkedHashMap<>();
        nfce.put("cnpj_emitente", cnpjEmitente);
        nfce.put("natureza_operacao", "Venda ao consumidor");
        nfce.put("presenca_comprador", "1");   // operação presencial
        nfce.put("modalidade_frete", "9");      // sem frete
        nfce.put("local_destino", "1");         // operação interna
        nfce.put("consumidor_final", "1");
        if (venda.getCliente() != null && venda.getCliente().getCpf() != null) {
            nfce.put("cpf_destinatario", venda.getCliente().getCpf());
            nfce.put("nome_destinatario", venda.getCliente().getNome());
        }
        nfce.put("valor_desconto", desconto.toPlainString());
        nfce.put("items", itensPayload);
        nfce.put("formas_pagamento", List.of(Map.of(
                "forma_pagamento", formaPagamentoSefaz(venda.getFormaPagamento()),
                "valor_pagamento", venda.getTotal().toPlainString())));
        return nfce;
    }

    private Map<String, Object> itemPayload(int numero, ItemVenda item, BigDecimal descItem) {
        Produto p = item.getVariacao().getProduto();
        BigDecimal qtd = BigDecimal.valueOf(item.getQuantidade());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("numero_item", String.valueOf(numero));
        m.put("codigo_produto", p.getCodigo());
        m.put("descricao", descricao(item.getVariacao()));
        m.put("cfop", valorOuPadrao(p.getCfop(), cfopPadrao));
        m.put("unidade_comercial", p.getUnidade() != null ? p.getUnidade() : "UN");
        m.put("quantidade_comercial", qtd.toPlainString());
        m.put("valor_unitario_comercial", item.getPrecoUnit().toPlainString());
        m.put("valor_bruto", item.getSubtotal().toPlainString());
        if (descItem.signum() > 0) m.put("valor_desconto", descItem.toPlainString());
        m.put("unidade_tributavel", p.getUnidade() != null ? p.getUnidade() : "UN");
        m.put("quantidade_tributavel", qtd.toPlainString());
        m.put("valor_unitario_tributavel", item.getPrecoUnit().toPlainString());
        m.put("ncm", p.getNcm());
        m.put("icms_origem", String.valueOf(p.getOrigem() != null ? p.getOrigem() : 0));
        m.put("icms_situacao_tributaria", valorOuPadrao(p.getCsosn(), csosnPadrao)); // CSOSN
        m.put("pis_situacao_tributaria", PIS_COFINS_SIMPLES);
        m.put("cofins_situacao_tributaria", PIS_COFINS_SIMPLES);
        return m;
    }

    private static String descricao(Variacao v) {
        StringJoiner sj = new StringJoiner(" ");
        sj.add(v.getProduto().getNome());
        if (v.getTamanho() != null) sj.add(v.getTamanho());
        if (v.getCor() != null) sj.add(v.getCor());
        return sj.toString();
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return valor != null && !valor.isBlank() ? valor : padrao;
    }
}
