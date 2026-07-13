package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id")
    private Vendedor vendedor;

    @Column(nullable = false)
    private Instant data = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", nullable = false)
    private FormaPagamento formaPagamento;

    @Column(nullable = false)
    private BigDecimal desconto = BigDecimal.ZERO;

    /** Total final cobrado (soma dos itens - desconto). */
    @Column(nullable = false)
    private BigDecimal total;

    /** Informativo: em quantas vezes passou no cartão (a maquininha cuida do resto). */
    @Column(name = "parcelas_cartao")
    private Integer parcelasCartao;

    /** Ex.: "comprou no nome da avó com autorização" — sai na promissória e no carnê. */
    private String observacao;

    /** Tipo da notinha: "Geral" ou "Tênis" — obrigatório no fechamento. */
    @Column(name = "tipo_notinha")
    private String tipoNotinha;

    /** CPF do consumidor p/ a NFC-e (informado no rodapé da venda); só dígitos, opcional. */
    private String cpf;

    /**
     * Estorno é marcação, nunca DELETE: preenchido = a venda saiu de todas as
     * somas financeiras (filtro cancelada_em IS NULL), mas o registro e a
     * numeração impressa continuam existindo para sempre.
     */
    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    @Column(name = "cancelada_por")
    private String canceladaPor;

    @Column(name = "cancelamento_motivo")
    private String cancelamentoMotivo;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemVenda> itens = new ArrayList<>();

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero")
    private List<ParcelaFiado> parcelas = new ArrayList<>();

    public void adicionarItem(ItemVenda item) {
        item.setVenda(this);
        itens.add(item);
    }

    public void adicionarParcela(ParcelaFiado parcela) {
        parcela.setVenda(this);
        parcelas.add(parcela);
    }

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Vendedor getVendedor() { return vendedor; }
    public void setVendedor(Vendedor vendedor) { this.vendedor = vendedor; }
    public Instant getData() { return data; }
    public void setData(Instant data) { this.data = data; }
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(FormaPagamento formaPagamento) { this.formaPagamento = formaPagamento; }
    public String getTipoNotinha() { return tipoNotinha; }
    public void setTipoNotinha(String tipoNotinha) { this.tipoNotinha = tipoNotinha; }
    public Instant getCanceladaEm() { return canceladaEm; }
    public String getCanceladaPor() { return canceladaPor; }
    public String getCancelamentoMotivo() { return cancelamentoMotivo; }
    public BigDecimal getDesconto() { return desconto; }
    public void setDesconto(BigDecimal desconto) { this.desconto = desconto; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public Integer getParcelasCartao() { return parcelasCartao; }
    public void setParcelasCartao(Integer parcelasCartao) { this.parcelasCartao = parcelasCartao; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public List<ItemVenda> getItens() { return itens; }
    public List<ParcelaFiado> getParcelas() { return parcelas; }
}
