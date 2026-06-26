package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Um contato de cobranca com um devedor. Histórico/CRM — nunca altera a dívida
 * (que continua calculada). Quando o cliente promete pagar, guarda a data
 * prometida para virar a fila de follow-up.
 */
@Entity
@Table(name = "contato_cobranca")
public class ContatoCobranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false)
    private Instant data = Instant.now();

    private String operador;

    @Column(nullable = false)
    private String canal;

    @Column(nullable = false)
    private String resultado;

    @Column(name = "promessa_data")
    private LocalDate promessaData;

    private String observacao;

    public Long getId() { return id; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public Instant getData() { return data; }
    public void setData(Instant data) { this.data = data; }
    public String getOperador() { return operador; }
    public void setOperador(String operador) { this.operador = operador; }
    public String getCanal() { return canal; }
    public void setCanal(String canal) { this.canal = canal; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public LocalDate getPromessaData() { return promessaData; }
    public void setPromessaData(LocalDate promessaData) { this.promessaData = promessaData; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
