package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Quanto um recebimento de carnê abateu de cada parcela — guarda o necessário
 * para REVERTER o recebimento (devolver o valor idêntico a cada parcela).
 * Espelha o baixa_fiado_item. Só existe para recebimentos feitos a partir da V25.
 */
@Entity
@Table(name = "recebimento_item")
public class RecebimentoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** id do PagamentoFiado (recebimento) que gerou este abate. */
    @Column(name = "pagamento_id", nullable = false)
    private Long pagamentoId;

    /** 'L' = carnê SET (pagamento_fiado) | 'V' = parcela_fiado */
    @Column(nullable = false)
    private String origem;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(nullable = false)
    private BigDecimal valor;

    public RecebimentoItem() {}

    public RecebimentoItem(Long pagamentoId, String origem, Long refId, BigDecimal valor) {
        this.pagamentoId = pagamentoId;
        this.origem = origem;
        this.refId = refId;
        this.valor = valor;
    }

    public Long getId() { return id; }
    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
