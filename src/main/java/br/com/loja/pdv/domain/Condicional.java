package br.com.loja.pdv.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Peças que a cliente levou para provar em casa. A saída não baixa estoque
 * (decisão do dono): só baixa quando fecha, e aí as peças que ela ficar viram
 * uma Venda normal. O que volta não mexe em nada.
 */
@Entity
public class Condicional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id")
    private Vendedor vendedor;

    @Column(name = "data_saida", nullable = false)
    private Instant dataSaida = Instant.now();

    @Column(nullable = false)
    private String status = "ABERTA";

    private String observacao;

    @Column(name = "venda_id")
    private Long vendaId;

    @Column(name = "data_fechamento")
    private Instant dataFechamento;

    @OneToMany(mappedBy = "condicional", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemCondicional> itens = new ArrayList<>();

    public Long getId() { return id; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public Vendedor getVendedor() { return vendedor; }
    public void setVendedor(Vendedor vendedor) { this.vendedor = vendedor; }
    public Instant getDataSaida() { return dataSaida; }
    public void setDataSaida(Instant dataSaida) { this.dataSaida = dataSaida; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
    public Long getVendaId() { return vendaId; }
    public void setVendaId(Long vendaId) { this.vendaId = vendaId; }
    public Instant getDataFechamento() { return dataFechamento; }
    public void setDataFechamento(Instant dataFechamento) { this.dataFechamento = dataFechamento; }
    public List<ItemCondicional> getItens() { return itens; }
}
