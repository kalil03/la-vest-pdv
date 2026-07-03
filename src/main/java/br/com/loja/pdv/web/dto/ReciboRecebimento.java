package br.com.loja.pdv.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Tudo que o recibo térmico do recebimento precisa. */
public record ReciboRecebimento(
        Long id,
        Instant data,
        String clienteNome,
        String vendedorNome,
        BigDecimal valor,
        String tipo,
        BigDecimal saldoAnterior,
        BigDecimal saldoRestante,
        List<Item> itens) {

    /** Uma parcela abatida: quitada quando restante == 0, parcial caso contrário. */
    public record Item(String descricao, Long notinha, LocalDate vencimento,
                       BigDecimal valorOriginal, BigDecimal valorAplicado, BigDecimal restante) {
    }
}
