package br.com.loja.pdv.web;

import br.com.loja.pdv.service.RegraNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Erros de negócio/validação viram 400 com mensagem legível para mostrar no caixa. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(RegraNegocioException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> regraNegocio(RegraNegocioException e) {
        return Map.of("erro", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validacao(MethodArgumentNotValidException e) {
        String mensagem = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Dados inválidos");
        return Map.of("erro", mensagem);
    }

    /** Constraint do banco (última linha de defesa) violada: ex. CPF ou código duplicado. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> integridade(DataIntegrityViolationException e) {
        log.warn("violação de integridade no banco", e);
        return Map.of("erro", "Registro conflita com outro já existente (ex.: CPF ou código duplicado)");
    }

    /**
     * Rede de segurança: qualquer erro não previsto vira 500 com mensagem
     * genérica no caixa e stack trace completo no log (journald) — sem isso o
     * diagnóstico em produção fica no escuro.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> inesperado(Exception e) throws Exception {
        // exceções do próprio Spring MVC (404 de estático, JSON malformado,
        // método errado…) voltam para o tratamento padrão do framework
        if (e instanceof ErrorResponse || e instanceof HttpMessageNotReadableException) {
            throw e;
        }
        log.error("erro não tratado na API", e);
        return Map.of("erro", "Erro inesperado no sistema — tente de novo; se repetir, anote a hora e avise");
    }
}
