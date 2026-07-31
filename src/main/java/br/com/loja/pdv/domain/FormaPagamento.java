package br.com.loja.pdv.domain;

public enum FormaPagamento {
    DINHEIRO, PIX, CARTAO, FIADO,
    /** Venda à vista paga em 2+ formas; o detalhamento fica em pagamento_venda. */
    MISTO
}
