package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * Uma forma de pagamento de uma venda à vista MISTA (ex.: "Cartão R$60").
 * Existe só quando a venda foi paga em 2+ formas; o Caixa soma por estas linhas.
 */
@Entity
@Table(name = "pagamento_venda")
public class PagamentoVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venda_id", nullable = false)
    private Long vendaId;

    /** 'DINHEIRO' | 'PIX' | 'CARTAO' */
    @Column(nullable = false)
    private String forma;

    @Column(nullable = false)
    private BigDecimal valor;

    public PagamentoVenda() {}

    public PagamentoVenda(Long vendaId, String forma, BigDecimal valor) {
        this.vendaId = vendaId;
        this.forma = forma;
        this.valor = valor;
    }

    public Long getId() { return id; }
    public Long getVendaId() { return vendaId; }
    public void setVendaId(Long vendaId) { this.vendaId = vendaId; }
    public String getForma() { return forma; }
    public void setForma(String forma) { this.forma = forma; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
