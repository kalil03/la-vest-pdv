package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity

@Table(name = "pagamento_fiado")
public class PagamentoFiado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    /** Preenchido quando o pagamento nasce junto de uma venda (entrada do fiado). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id")
    private Venda venda;

    /** Funcionário que recebeu (recebimento de carnê). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id")
    private Vendedor vendedor;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPagamentoFiado tipo;

    @Column(nullable = false)
    private Instant data = Instant.now();

    /** Só para DEBITO_INICIAL: quanto desta parcela migrada ainda falta pagar. */
    @Column(name = "valor_aberto")
    private BigDecimal valorAberto;

    /** Resumo legível do que um recebimento quitou (ex.: "Venda nº 12 (2/3)"). */
    private String detalhe;

    /** Nº da notinha no sistema antigo (NDOC, ex. "66/01") — só em registros migrados. */
    private String documento;

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Venda getVenda() { return venda; }
    public void setVenda(Venda venda) { this.venda = venda; }
    public Vendedor getVendedor() { return vendedor; }
    public void setVendedor(Vendedor vendedor) { this.vendedor = vendedor; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public TipoPagamentoFiado getTipo() { return tipo; }
    public void setTipo(TipoPagamentoFiado tipo) { this.tipo = tipo; }
    public Instant getData() { return data; }
    public BigDecimal getValorAberto() { return valorAberto; }
    public void setValorAberto(BigDecimal valorAberto) { this.valorAberto = valorAberto; }
    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
}
