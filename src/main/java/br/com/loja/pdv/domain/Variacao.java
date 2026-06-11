package br.com.loja.pdv.domain;

import jakarta.persistence.*;

@Entity
public class Variacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    private String tamanho;

    private String cor;

    @Column(nullable = false)
    private int estoque;

    /** Variação "padrão" de produto sem grade (perfume): a interface a esconde. */
    public boolean isPadrao() {
        return tamanho == null && cor == null;
    }

    public Long getId() { return id; }
    public Produto getProduto() { return produto; }
    public void setProduto(Produto produto) { this.produto = produto; }
    public String getTamanho() { return tamanho; }
    public void setTamanho(String tamanho) { this.tamanho = tamanho; }
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    public int getEstoque() { return estoque; }
    public void setEstoque(int estoque) { this.estoque = estoque; }
}
