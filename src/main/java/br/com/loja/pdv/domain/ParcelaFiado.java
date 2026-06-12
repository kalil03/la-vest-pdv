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

    /**
     * Quanto desta parcela ainda falta pagar. É rateio de recebimento
     * (por ordem de seleção no balcão), não saldo: a dívida total continua
     * sendo sempre calculada. Invariante: SUM(valor_aberto) == saldo devedor.
     */
    @Column(name = "valor_aberto", nullable = false)
    private BigDecimal valorAberto;

    @Column(nullable = false)
    private LocalDate vencimento;

    public Long getId() { return id; }
    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }
    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public BigDecimal getValorAberto() { return valorAberto; }
    public void setValorAberto(BigDecimal valorAberto) { this.valorAberto = valorAberto; }
    public LocalDate getVencimento() { return vencimento; }
    public void setVencimento(LocalDate vencimento) { this.vencimento = vencimento; }
}
