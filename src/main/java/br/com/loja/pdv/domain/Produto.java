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

    private String csosn;          // null = usa o padrão da loja (fiscal.csosn-padrao)
    private String cfop;           // null = usa o padrão da loja (fiscal.cfop-padrao)

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Variacao> variacoes = new ArrayList<>();

    public void adicionarVariacao(Variacao variacao) {
        variacao.setProduto(this);
        variacoes.add(variacao);
    }

    
    // -- Legado: Custos e margens --
    private BigDecimal precoCusto;
    private BigDecimal precoCompra;
    private BigDecimal pLucro;
    @Column(name = "p_lucro2")
    private BigDecimal pLucro2;
    @Column(name = "p_lucro3")
    private BigDecimal pLucro3;
    private BigDecimal vLucro;
    @Column(name = "v_lucro2")
    private BigDecimal vLucro2;
    @Column(name = "v_lucro3")
    private BigDecimal vLucro3;

    // -- Legado: Tabelas de Preço Múltiplas --
    @Column(name = "preco_venda2")
    private BigDecimal precoVenda2;
    @Column(name = "preco_venda3")
    private BigDecimal precoVenda3;
    private BigDecimal precoEspecial;

    // -- Legado: Estoque Avançado --
    private BigDecimal qtdeMin;
    private BigDecimal qtdeMax;
    private BigDecimal qtdeReservado;
    private BigDecimal qtdeProducao;

    // -- Legado: Hierarquia --
    private String grupo;
    private String subgrupo;

    // -- Legado: Códigos Auxiliares --
    private String refOrigi;
    private String codFabricante;

    // -- Legado: Flags e Configurações --
    private String controlaEstoque;
    private String situacao;
    private String prodProp;
    private String classe;
    private String prodEspecifico;

    // -- Legado: Fiscal --
    private String cstIcms;
    private String cbenef;

    // -- Legado: Auditoria --
    private Instant dataCad;
    private Instant dataAlt;
    @Column(name = "data_alt_ncm")
    private Instant dataAltNcm;
    private String usuarioCad;
    private String usuarioAlt;

    // -- Legado: Gerais --
    @Column(length = 2000)
    private String anota;
    private String unCompra;


    public BigDecimal getPrecoCusto() { return precoCusto; }
    public void setPrecoCusto(BigDecimal precoCusto) { this.precoCusto = precoCusto; }
    public BigDecimal getPrecoCompra() { return precoCompra; }
    public void setPrecoCompra(BigDecimal precoCompra) { this.precoCompra = precoCompra; }
    public BigDecimal getPLucro() { return pLucro; }
    public void setPLucro(BigDecimal pLucro) { this.pLucro = pLucro; }
    public BigDecimal getPLucro2() { return pLucro2; }
    public void setPLucro2(BigDecimal pLucro2) { this.pLucro2 = pLucro2; }
    public BigDecimal getPLucro3() { return pLucro3; }
    public void setPLucro3(BigDecimal pLucro3) { this.pLucro3 = pLucro3; }
    public BigDecimal getVLucro() { return vLucro; }
    public void setVLucro(BigDecimal vLucro) { this.vLucro = vLucro; }
    public BigDecimal getVLucro2() { return vLucro2; }
    public void setVLucro2(BigDecimal vLucro2) { this.vLucro2 = vLucro2; }
    public BigDecimal getVLucro3() { return vLucro3; }
    public void setVLucro3(BigDecimal vLucro3) { this.vLucro3 = vLucro3; }
    public BigDecimal getPrecoVenda2() { return precoVenda2; }
    public void setPrecoVenda2(BigDecimal precoVenda2) { this.precoVenda2 = precoVenda2; }
    public BigDecimal getPrecoVenda3() { return precoVenda3; }
    public void setPrecoVenda3(BigDecimal precoVenda3) { this.precoVenda3 = precoVenda3; }
    public BigDecimal getPrecoEspecial() { return precoEspecial; }
    public void setPrecoEspecial(BigDecimal precoEspecial) { this.precoEspecial = precoEspecial; }
    public BigDecimal getQtdeMin() { return qtdeMin; }
    public void setQtdeMin(BigDecimal qtdeMin) { this.qtdeMin = qtdeMin; }
    public BigDecimal getQtdeMax() { return qtdeMax; }
    public void setQtdeMax(BigDecimal qtdeMax) { this.qtdeMax = qtdeMax; }
    public BigDecimal getQtdeReservado() { return qtdeReservado; }
    public void setQtdeReservado(BigDecimal qtdeReservado) { this.qtdeReservado = qtdeReservado; }
    public BigDecimal getQtdeProducao() { return qtdeProducao; }
    public void setQtdeProducao(BigDecimal qtdeProducao) { this.qtdeProducao = qtdeProducao; }
    public String getGrupo() { return grupo; }
    public void setGrupo(String grupo) { this.grupo = grupo; }
    public String getSubgrupo() { return subgrupo; }
    public void setSubgrupo(String subgrupo) { this.subgrupo = subgrupo; }
    public String getRefOrigi() { return refOrigi; }
    public void setRefOrigi(String refOrigi) { this.refOrigi = refOrigi; }
    public String getCodFabricante() { return codFabricante; }
    public void setCodFabricante(String codFabricante) { this.codFabricante = codFabricante; }
    public String getControlaEstoque() { return controlaEstoque; }
    public void setControlaEstoque(String controlaEstoque) { this.controlaEstoque = controlaEstoque; }
    public String getSituacao() { return situacao; }
    public void setSituacao(String situacao) { this.situacao = situacao; }
    public String getProdProp() { return prodProp; }
    public void setProdProp(String prodProp) { this.prodProp = prodProp; }
    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }
    public String getProdEspecifico() { return prodEspecifico; }
    public void setProdEspecifico(String prodEspecifico) { this.prodEspecifico = prodEspecifico; }
    public String getCstIcms() { return cstIcms; }
    public void setCstIcms(String cstIcms) { this.cstIcms = cstIcms; }
    public String getCbenef() { return cbenef; }
    public void setCbenef(String cbenef) { this.cbenef = cbenef; }
    public Instant getDataCad() { return dataCad; }
    public void setDataCad(Instant dataCad) { this.dataCad = dataCad; }
    public Instant getDataAlt() { return dataAlt; }
    public void setDataAlt(Instant dataAlt) { this.dataAlt = dataAlt; }
    public Instant getDataAltNcm() { return dataAltNcm; }
    public void setDataAltNcm(Instant dataAltNcm) { this.dataAltNcm = dataAltNcm; }
    public String getUsuarioCad() { return usuarioCad; }
    public void setUsuarioCad(String usuarioCad) { this.usuarioCad = usuarioCad; }
    public String getUsuarioAlt() { return usuarioAlt; }
    public void setUsuarioAlt(String usuarioAlt) { this.usuarioAlt = usuarioAlt; }
    public String getAnota() { return anota; }
    public void setAnota(String anota) { this.anota = anota; }
    public String getUnCompra() { return unCompra; }
    public void setUnCompra(String unCompra) { this.unCompra = unCompra; }
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
    public String getCsosn() { return csosn; }
    public void setCsosn(String csosn) { this.csosn = csosn; }
    public String getCfop() { return cfop; }
    public void setCfop(String cfop) { this.cfop = cfop; }
    public List<Variacao> getVariacoes() { return variacoes; }
}
