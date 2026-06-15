package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Rastreio de uma NFC-e emitida para uma venda (via Focus NFe). */
@Entity
public class Nfce {

    public enum Status { PROCESSANDO, AUTORIZADO, ERRO, CANCELADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    /** Referência idempotente enviada à Focus (ex.: "venda-123"). */
    @Column(nullable = false, unique = true)
    private String ref;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PROCESSANDO;

    @Column(name = "chave_acesso")
    private String chaveAcesso;

    private Integer numero;
    private Integer serie;
    private String protocolo;

    @Column(name = "danfe_url")
    private String danfeUrl;

    @Column(name = "xml_url")
    private String xmlUrl;

    private String mensagem;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm = Instant.now();

    @Column(name = "autorizada_em")
    private Instant autorizadaEm;

    public Long getId() { return id; }
    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }
    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getChaveAcesso() { return chaveAcesso; }
    public void setChaveAcesso(String chaveAcesso) { this.chaveAcesso = chaveAcesso; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public Integer getSerie() { return serie; }
    public void setSerie(Integer serie) { this.serie = serie; }
    public String getProtocolo() { return protocolo; }
    public void setProtocolo(String protocolo) { this.protocolo = protocolo; }
    public String getDanfeUrl() { return danfeUrl; }
    public void setDanfeUrl(String danfeUrl) { this.danfeUrl = danfeUrl; }
    public String getXmlUrl() { return xmlUrl; }
    public void setXmlUrl(String xmlUrl) { this.xmlUrl = xmlUrl; }
    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAutorizadaEm() { return autorizadaEm; }
    public void setAutorizadaEm(Instant autorizadaEm) { this.autorizadaEm = autorizadaEm; }
}
