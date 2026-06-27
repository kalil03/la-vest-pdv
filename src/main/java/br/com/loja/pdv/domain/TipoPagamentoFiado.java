package br.com.loja.pdv.domain;

public enum TipoPagamentoFiado {
    DINHEIRO, PIX, CARTAO, VALE_CREDITO,
    /** Parcela em aberto migrada do crediário do SET — valor sempre NEGATIVO (soma dívida). */
    DEBITO_INICIAL,
    /** Baixa por incobrabilidade — positivo, reduz o saldo mas NÃO é dinheiro (fora do caixa/recebido). Reversível. */
    BAIXA
}
