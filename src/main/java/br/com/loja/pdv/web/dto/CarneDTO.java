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
     * valorAberto vem do rateio dos recebimentos (por ordem de seleção no
     * balcão). O saldo devedor continua 100% calculado — invariante:
     * SUM(valorAberto) == saldoDevedor.
     */
    public record Parcela(
            String id,            // "L123" = carnê migrado do SET, "V45" = parcela de venda nossa
            String descricao,
            Long notinha,         // nº da venda (null nas parcelas migradas do SET)
            String observacao,    // observação da venda ("comprou no nome da avó"), se houver
            LocalDate vencimento,
            BigDecimal valor,
            BigDecimal valorAberto,
            long diasAtraso,      // negativo = ainda vai vencer (ex.: -5 → vence em 5 dias)
            String tipo,          // "Tênis" | "Geral" — separa a carteira nas telas
            String notaKey,       // chave da nota (agrupa as parcelas na tela): "L:002841.07" | "V:78"
            String notaRotulo,    // rótulo da nota ("002841.07" | "Venda nº 78")
            String parcelaRotulo) { // rótulo da parcela dentro da nota ("03" | "2/3")
    }

    public record Pagamento(Instant data, BigDecimal valor, String tipo, String vendedorNome,
                            String detalhe) {
    }
}
