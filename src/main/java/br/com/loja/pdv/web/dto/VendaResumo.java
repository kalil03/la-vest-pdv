package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.FormaPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Tudo que a notinha/promissória precisa para ser impressa. */
public record VendaResumo(
        Long id,
        Instant data,
        FormaPagamento formaPagamento,
        BigDecimal total,
        String clienteNome,
        BigDecimal saldoDevedor, // só preenchido em venda FIADO (saldo já incluindo esta venda)
        List<Item> itens) {

    public record Item(String descricao, int quantidade, BigDecimal precoUnit, BigDecimal subtotal) {
    }
}
