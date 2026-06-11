package br.com.loja.pdv.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record NovoProdutoRequest(
        String codigo,
        @NotBlank(message = "Nome é obrigatório") String nome,
        String categoria,
        @NotNull(message = "Preço é obrigatório") @PositiveOrZero BigDecimal preco,
        @Valid List<NovaVariacao> variacoes) {

    public record NovaVariacao(String tamanho, String cor, @PositiveOrZero int estoque) {
    }
}
