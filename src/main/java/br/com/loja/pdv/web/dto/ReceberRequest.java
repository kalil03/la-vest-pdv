package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.TipoPagamentoFiado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

/**
 * Recebimento com rateio POR ORDEM DE SELEÇÃO: a tela manda quanto abater de
 * cada parcela (ids "L.." = carnê SET, "V.." = parcela de venda). A soma das
 * alocações deve fechar exatamente com o valor recebido.
 */
public record ReceberRequest(
        @NotNull Long clienteId,
        @NotNull @Positive(message = "Valor do recebimento deve ser positivo")
        @Digits(integer = 8, fraction = 2, message = "Valor com mais de 2 casas decimais")
        BigDecimal valor,
        @NotNull(message = "Informe a forma de pagamento") TipoPagamentoFiado tipo,
        @NotNull(message = "Informe o funcionário que está recebendo") Long vendedorId,
        @NotEmpty(message = "Selecione ao menos uma parcela") @Valid List<Alocacao> alocacoes) {

    public record Alocacao(
            @NotBlank String parcelaId,
            @NotNull @Positive(message = "Valor da parcela deve ser positivo")
            @Digits(integer = 8, fraction = 2, message = "Valor da parcela com mais de 2 casas decimais")
            BigDecimal valor) {
    }
}
