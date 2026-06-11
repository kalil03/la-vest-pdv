package br.com.loja.pdv.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
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
        List<CarneDTO.Parcela> parcelasQuitadas,
        CarneDTO.Parcela parcelaParcial) { // null se o valor fechou exato
}
