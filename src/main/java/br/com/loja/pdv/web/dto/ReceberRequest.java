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
        // forma única (compat) — quando o cliente paga em uma forma só
        TipoPagamentoFiado tipo,
        // OU várias formas (dinheiro + PIX etc.); a soma tem que fechar com o valor
        @Valid List<FormaPaga> formas,
        @NotNull(message = "Informe o funcionário que está recebendo") Long vendedorId,
        @NotEmpty(message = "Selecione ao menos uma parcela") @Valid List<Alocacao> alocacoes) {

    public record Alocacao(
            @NotBlank String parcelaId,
            @NotNull @Positive(message = "Valor da parcela deve ser positivo")
            @Digits(integer = 8, fraction = 2, message = "Valor da parcela com mais de 2 casas decimais")
            BigDecimal valor) {
    }

    /** Uma forma do recebimento e quanto foi pago nela. */
    public record FormaPaga(
            @NotNull(message = "Informe a forma de pagamento") TipoPagamentoFiado tipo,
            @NotNull @Positive(message = "Valor da forma deve ser positivo")
            @Digits(integer = 8, fraction = 2, message = "Valor da forma com mais de 2 casas decimais")
            BigDecimal valor) {
    }
}
