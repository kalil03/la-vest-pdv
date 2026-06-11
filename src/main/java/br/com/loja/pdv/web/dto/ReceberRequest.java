package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.TipoPagamentoFiado;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ReceberRequest(
        @NotNull Long clienteId,
        @NotNull @Positive(message = "Valor do recebimento deve ser positivo") BigDecimal valor,
        @NotNull(message = "Informe a forma de pagamento") TipoPagamentoFiado tipo,
        @NotNull(message = "Informe o funcionário que está recebendo") Long vendedorId) {
}
