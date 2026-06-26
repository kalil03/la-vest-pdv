package br.com.loja.pdv.web.dto;

import java.math.BigDecimal;

/** "Score da casa": badge exibido ao selecionar o cliente na venda. */
public record ScoreCliente(BigDecimal saldoDevedor, Double prazoMedioDias,
                           BigDecimal valorVencido, long parcelasVencidas) {
}
