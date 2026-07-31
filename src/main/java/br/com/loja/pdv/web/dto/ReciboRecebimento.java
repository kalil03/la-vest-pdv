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
        List<Item> itens,
        List<NotaResumo> notasEmAberto,
        List<FormaPaga> formas) {

    /** Uma forma usada no recebimento (ex.: Dinheiro R$60) — >1 quando o pagamento é misto. */
    public record FormaPaga(String tipo, BigDecimal valor) {
    }

    /** Uma parcela abatida: quitada quando restante == 0, parcial caso contrário. */
    public record Item(String descricao, Long notinha, LocalDate vencimento,
                       BigDecimal valorOriginal, BigDecimal valorAplicado, BigDecimal restante,
                       String notaKey, String notaRotulo) {
    }

    /** Uma nota do cliente ainda em aberto DEPOIS deste pagamento (para o cliente ver o todo). */
    public record NotaResumo(String rotulo, String tipo, BigDecimal totalAberto) {
    }
}
