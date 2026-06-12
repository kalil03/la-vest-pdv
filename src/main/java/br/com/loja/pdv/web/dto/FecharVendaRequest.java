package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.FormaPagamento;
import br.com.loja.pdv.domain.TipoPagamentoFiado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cliente pode vir por id (já cadastrado) OU só por nome (cadastro na hora,
 * sem fricção). Os dois nulos = venda sem cliente (à vista).
 */
public record FecharVendaRequest(
        Long clienteId,
        String clienteNome,
        String clienteTelefone,
        Long vendedorId,
        @NotNull(message = "Forma de pagamento é obrigatória") FormaPagamento formaPagamento,
        @PositiveOrZero(message = "Desconto não pode ser negativo") BigDecimal desconto,
        Integer parcelasCartao,
        String observacao,
        @Valid Fiado fiado,
        @NotEmpty(message = "Venda sem itens") @Valid List<Item> itens) {

    public record Item(
            @NotNull Long variacaoId,
            @Min(value = 1, message = "Quantidade deve ser positiva") int quantidade,
            @PositiveOrZero(message = "Preço não pode ser negativo") BigDecimal precoUnit) {
    }

    /**
     * Condições do fiado: entrada opcional + cronograma de parcelas.
     * A soma das parcelas deve fechar exatamente com (total - entrada);
     * o servidor valida — a tela faz o recálculo, o servidor garante.
     */
    public record Fiado(
            @PositiveOrZero(message = "Entrada não pode ser negativa") BigDecimal entradaValor,
            TipoPagamentoFiado entradaTipo,
            @Valid List<Parcela> parcelas) {

        public record Parcela(
                @Min(1) int numero,
                @NotNull BigDecimal valor,
                @NotNull LocalDate vencimento) {
        }
    }
}
