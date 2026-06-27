package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Baixa de fiado por incobrabilidade — REVERSÍVEL. Zera o valor_aberto das
 * parcelas (guardadas nos itens, para restaurar) e cria um PagamentoFiado tipo
 * BAIXA que reduz o saldo sem ser dinheiro. Auditoria: nunca apagada, vira
 * REVERTIDA ao restaurar.
 */
@Entity
@Table(name = "baixa_fiado")
public class BaixaFiado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private Instant data = Instant.now();

    private String operador;
    private String motivo;

    @Column(nullable = false)
    private BigDecimal valor;

    @Column(nullable = false)
    private String status = "ATIVA";

    @Column(name = "pagamento_id")
    private Long pagamentoId;

    @Column(name = "data_reversao")
    private Instant dataReversao;

    @Column(name = "operador_reversao")
    private String operadorReversao;

    @OneToMany(mappedBy = "baixa", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BaixaFiadoItem> itens = new ArrayList<>();

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public Instant getData() { return data; }
    public void setData(Instant data) { this.data = data; }
    public String getOperador() { return operador; }
    public void setOperador(String operador) { this.operador = operador; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPagamentoId() { return pagamentoId; }
    public void setPagamentoId(Long pagamentoId) { this.pagamentoId = pagamentoId; }
    public Instant getDataReversao() { return dataReversao; }
    public void setDataReversao(Instant dataReversao) { this.dataReversao = dataReversao; }
    public String getOperadorReversao() { return operadorReversao; }
    public void setOperadorReversao(String operadorReversao) { this.operadorReversao = operadorReversao; }
    public List<BaixaFiadoItem> getItens() { return itens; }
}
