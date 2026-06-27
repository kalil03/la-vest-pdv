package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Quanto saiu de cada parcela numa baixa — guarda o necessário para restaurar idêntico. */
@Entity
@Table(name = "baixa_fiado_item")
public class BaixaFiadoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "baixa_id")
    private BaixaFiado baixa;

    /** 'L' = carnê SET (pagamento_fiado) | 'V' = parcela_fiado */
    @Column(nullable = false)
    private String origem;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(nullable = false)
    private BigDecimal valor;

    public BaixaFiadoItem() {}

    public BaixaFiadoItem(BaixaFiado baixa, String origem, Long refId, BigDecimal valor) {
        this.baixa = baixa;
        this.origem = origem;
        this.refId = refId;
        this.valor = valor;
    }

    public Long getId() { return id; }
    public BaixaFiado getBaixa() { return baixa; }
    public void setBaixa(BaixaFiado baixa) { this.baixa = baixa; }
    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }
    public Long getRefId() { return refId; }
    public void setRefId(Long refId) { this.refId = refId; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
