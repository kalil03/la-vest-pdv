package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.FormaPagamento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cliente pode vir por id (já cadastrado) OU só por nome (cadastro na hora,
 * sem fricção — regra de ouro nº 5). Os dois nulos = venda sem cliente.
 */
public record FecharVendaRequest(
        Long clienteId,
        String clienteNome,
        String clienteTelefone,
        @NotNull(message = "Forma de pagamento é obrigatória") FormaPagamento formaPagamento,
        @NotEmpty(message = "Venda sem itens") @Valid List<Item> itens) {

    public record Item(
            @NotNull Long variacaoId,
            @Min(value = 1, message = "Quantidade deve ser positiva") int quantidade,
            @PositiveOrZero(message = "Preço não pode ser negativo") BigDecimal precoUnit) {
    }
}
