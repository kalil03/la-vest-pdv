package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.FormaPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Tudo que a notinha/promissória precisa para ser impressa. */
public record VendaResumo(
        Long id,
        Instant data,
        FormaPagamento formaPagamento,
        BigDecimal subtotal,
        BigDecimal desconto,
        BigDecimal total,
        String clienteNome,
        String vendedorNome,
        Integer parcelasCartao,
        BigDecimal entrada,       // só em FIADO com entrada
        BigDecimal saldoDevedor,  // só em FIADO (saldo já incluindo esta venda)
        List<Item> itens,
        List<Parcela> parcelas) { // só em FIADO

    public record Item(String descricao, int quantidade, BigDecimal precoUnit, BigDecimal subtotal) {
    }

    public record Parcela(int numero, BigDecimal valor, LocalDate vencimento) {
    }
}
