package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "item_venda")
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variacao_id")
    private Variacao variacao;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "preco_unit", nullable = false)
    private BigDecimal precoUnit;

    public BigDecimal getSubtotal() {
        return precoUnit.multiply(BigDecimal.valueOf(quantidade));
    }

    public Long getId() { return id; }
    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }
    public Variacao getVariacao() { return variacao; }
    public void setVariacao(Variacao variacao) { this.variacao = variacao; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    public BigDecimal getPrecoUnit() { return precoUnit; }
    public void setPrecoUnit(BigDecimal precoUnit) { this.precoUnit = precoUnit; }
}
