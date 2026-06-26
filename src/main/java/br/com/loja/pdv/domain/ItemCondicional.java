package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Uma peça que saiu na condicional, com o preço congelado no momento da saída. */
@Entity
@Table(name = "item_condicional")
public class ItemCondicional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condicional_id")
    private Condicional condicional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variacao_id")
    private Variacao variacao;

    @Column(nullable = false)
    private int quantidade;

    @Column(name = "preco_unit", nullable = false)
    private BigDecimal precoUnit;

    public Long getId() { return id; }
    public Condicional getCondicional() { return condicional; }
    public void setCondicional(Condicional condicional) { this.condicional = condicional; }
    public Variacao getVariacao() { return variacao; }
    public void setVariacao(Variacao variacao) { this.variacao = variacao; }
    public int getQuantidade() { return quantidade; }
    public void setQuantidade(int quantidade) { this.quantidade = quantidade; }
    public BigDecimal getPrecoUnit() { return precoUnit; }
    public void setPrecoUnit(BigDecimal precoUnit) { this.precoUnit = precoUnit; }
}
