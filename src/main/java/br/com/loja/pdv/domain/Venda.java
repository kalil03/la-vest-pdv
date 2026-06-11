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
    public FormaPagamento getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(FormaPagamento formaPagamento) { this.formaPagamento = formaPagamento; }
    public BigDecimal getDesconto() { return desconto; }
    public void setDesconto(BigDecimal desconto) { this.desconto = desconto; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public Integer getParcelasCartao() { return parcelasCartao; }
    public void setParcelasCartao(Integer parcelasCartao) { this.parcelasCartao = parcelasCartao; }
    public List<ItemVenda> getItens() { return itens; }
    public List<ParcelaFiado> getParcelas() { return parcelas; }
}
