package br.com.loja.pdv.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Visão do carnê de um cliente: saldo, parcelas em aberto e histórico recente. */
public record CarneDTO(
        ClienteDTO cliente,
        BigDecimal saldoDevedor,
        int parcelasAbertas,
        LocalDate vencimentoMaisAntigo, // null se nada em aberto
        List<Parcela> parcelas,
        List<Pagamento> ultimosPagamentos) {

    /**
     * Status calculado por alocação FIFO dos pagamentos nas parcelas mais
     * antigas — nada de flag "paga" gravada (regra de ouro nº 1).
     */
    public record Parcela(
            String id,            // "L123" = carnê migrado do SET, "V45" = parcela de venda nossa
            String descricao,
            LocalDate vencimento,
            BigDecimal valor,
            BigDecimal valorAberto, // < valor quando parcialmente coberta
            long diasAtraso) {
    }

    public record Pagamento(Instant data, BigDecimal valor, String tipo, String vendedorNome) {
    }
}
