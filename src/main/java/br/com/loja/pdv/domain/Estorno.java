package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Registro permanente de venda desfeita — auditoria, nunca é apagado. */
@Entity
public class Estorno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venda_id", nullable = false)
    private Long vendaId;

    @Column(nullable = false)
    private Instant data = Instant.now();

    private String operador;
    private String motivo;

    @Column(name = "cliente_nome")
    private String clienteNome;

    @Column(name = "forma_pagamento", nullable = false)
    private String formaPagamento;

    @Column(nullable = false)
    private BigDecimal total;

    private String resumo;

    public Long getId() { return id; }
    public Long getVendaId() { return vendaId; }
    public void setVendaId(Long vendaId) { this.vendaId = vendaId; }
    public Instant getData() { return data; }
    public String getOperador() { return operador; }
    public void setOperador(String operador) { this.operador = operador; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getClienteNome() { return clienteNome; }
    public void setClienteNome(String clienteNome) { this.clienteNome = clienteNome; }
    public String getFormaPagamento() { return formaPagamento; }
    public void setFormaPagamento(String formaPagamento) { this.formaPagamento = formaPagamento; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getResumo() { return resumo; }
    public void setResumo(String resumo) { this.resumo = resumo; }
}
