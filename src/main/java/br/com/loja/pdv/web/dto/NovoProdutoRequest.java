package br.com.loja.pdv.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/** Usado tanto na criação quanto na edição de produto. */
public record NovoProdutoRequest(
        String codigo,
        @NotBlank(message = "Nome é obrigatório") String nome,
        String categoria,
        String marcaNome,   // marca criada na hora se ainda não existir
        @NotNull(message = "Preço é obrigatório") @PositiveOrZero BigDecimal preco,
        String ncm,         // opcional: a nota fiscal é emitida pelo Set, não por este sistema
        String cest,
        String unidade,
        String codigoBarras,
        Integer origem,
        BigDecimal pCusto,
        BigDecimal pLucro,
        BigDecimal pAtacado,
        BigDecimal pLucroAtacado,
        BigDecimal estoque,
        BigDecimal estMinimo,
        @Valid List<NovaVariacao> variacoes) {

    public record NovaVariacao(String tamanho, String cor) {
    }
}
