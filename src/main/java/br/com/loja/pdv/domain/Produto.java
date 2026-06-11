package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private String nome;

    private String categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @Column(nullable = false)
    private BigDecimal preco;

    @Column(name = "data_criacao", nullable = false)
    private Instant dataCriacao = Instant.now();

    // ---- dados fiscais mínimos para a NFC-e (Fase 2) ----
    private String ncm;            // 8 dígitos
    private String cest;

    @Column(nullable = false)
    private String unidade = "UN";

    @Column(name = "codigo_barras")
    private String codigoBarras;   // GTIN/EAN

    @Column(nullable = false)
    private Integer origem = 0;    // 0=nacional, 1/2=estrangeira

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Variacao> variacoes = new ArrayList<>();

    public void adicionarVariacao(Variacao variacao) {
        variacao.setProduto(this);
        variacoes.add(variacao);
    }

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public Marca getMarca() { return marca; }
    public void setMarca(Marca marca) { this.marca = marca; }
    public BigDecimal getPreco() { return preco; }
    public void setPreco(BigDecimal preco) { this.preco = preco; }
    public Instant getDataCriacao() { return dataCriacao; }
    public String getNcm() { return ncm; }
    public void setNcm(String ncm) { this.ncm = ncm; }
    public String getCest() { return cest; }
    public void setCest(String cest) { this.cest = cest; }
    public String getUnidade() { return unidade; }
    public void setUnidade(String unidade) { this.unidade = unidade; }
    public String getCodigoBarras() { return codigoBarras; }
    public void setCodigoBarras(String codigoBarras) { this.codigoBarras = codigoBarras; }
    public Integer getOrigem() { return origem; }
    public void setOrigem(Integer origem) { this.origem = origem; }
    public List<Variacao> getVariacoes() { return variacoes; }
}
