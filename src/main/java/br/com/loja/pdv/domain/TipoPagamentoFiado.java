package br.com.loja.pdv.domain;

public enum TipoPagamentoFiado {
    DINHEIRO, PIX, CARTAO, VALE_CREDITO,
    /** Parcela em aberto migrada do crediário do SET — valor sempre NEGATIVO (soma dívida). */
    DEBITO_INICIAL
}
