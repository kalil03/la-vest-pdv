package br.com.loja.pdv.service;

/** Violação de regra de negócio: vira HTTP 400 com a mensagem para o caixa. */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
