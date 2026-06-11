package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma parcela do carnê de uma venda fiado. É cronograma combinado com o
 * cliente, não saldo: a dívida continua sendo sempre calculada
 * (vendas FIADO - pagamentos), nunca armazenada.
 */
@Entity
@Table(name = "parcela_fiado")
public class ParcelaFiado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDate vencimento;

    public Long getId() { return id; }
    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public LocalDate getVencimento() { return vencimento; }
    public void setVencimento(LocalDate vencimento) { this.vencimento = vencimento; }
}
