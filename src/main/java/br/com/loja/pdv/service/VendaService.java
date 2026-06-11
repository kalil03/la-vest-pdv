package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.repository.ClienteRepository;
import br.com.loja.pdv.repository.VariacaoRepository;
import br.com.loja.pdv.repository.VendaRepository;
import br.com.loja.pdv.web.dto.FecharVendaRequest;
import br.com.loja.pdv.web.dto.VendaResumo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.StringJoiner;

@Service
public class VendaService {

    private final VendaRepository vendaRepository;
    private final VariacaoRepository variacaoRepository;
    private final ClienteRepository clienteRepository;

    public VendaService(VendaRepository vendaRepository,
                        VariacaoRepository variacaoRepository,
                        ClienteRepository clienteRepository) {
        this.vendaRepository = vendaRepository;
        this.variacaoRepository = variacaoRepository;
        this.clienteRepository = clienteRepository;
    }

    /**
     * Regra de ouro nº 3: fechar venda é atômico. Gravar Venda + Itens + baixar
     * estoque acontece numa única transação — qualquer falha desfaz tudo.
     *
     * O fiado não precisa de escrita extra: a própria linha da Venda com
     * forma_pagamento = FIADO é o lançamento no carnê (a dívida é sempre
     * calculada, nunca armazenada — regra de ouro nº 1).
     */
    @Transactional
    public VendaResumo fechar(FecharVendaRequest req) {
        Cliente cliente = resolverCliente(req);
        if (req.formaPagamento() == FormaPagamento.FIADO && cliente == null) {
            throw new RegraNegocioException("Venda fiado precisa de um cliente");
        }

        Venda venda = new Venda();
        venda.setCliente(cliente);
        venda.setFormaPagamento(req.formaPagamento());

        BigDecimal total = BigDecimal.ZERO;
        for (FecharVendaRequest.Item itemReq : req.itens()) {
            Variacao variacao = variacaoRepository.findById(itemReq.variacaoId())
                    .orElseThrow(() -> new RegraNegocioException(
                            "Produto/variação não encontrado (id " + itemReq.variacaoId() + ")"));

            // Baixa de estoque como efeito colateral da venda, na mesma transação
            // (regra de ouro nº 2). UPDATE atômico direto no banco.
            variacaoRepository.baixarEstoque(variacao.getId(), itemReq.quantidade());

            // Preço pode vir da tela (desconto negociado no balcão); ausente, vale o preço de tabela
            BigDecimal precoUnit = itemReq.precoUnit() != null
                    ? itemReq.precoUnit()
                    : variacao.getProduto().getPreco();

            ItemVenda item = new ItemVenda();
            item.setVariacao(variacao);
            item.setQuantidade(itemReq.quantidade());
            item.setPrecoUnit(precoUnit);
            venda.adicionarItem(item);

            total = total.add(item.getSubtotal());
        }
        venda.setTotal(total);

        // saveAndFlush: o INSERT precisa estar no banco antes da query nativa
        // de saldo devedor (que deve enxergar ESTA venda no caso de fiado)
        vendaRepository.saveAndFlush(venda);
        return montarResumo(venda);
    }

    @Transactional(readOnly = true)
    public VendaResumo buscarResumo(Long id) {
        Venda venda = vendaRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Venda não encontrada (id " + id + ")"));
        return montarResumo(venda);
    }

    private Cliente resolverCliente(FecharVendaRequest req) {
        if (req.clienteId() != null) {
            return clienteRepository.findById(req.clienteId())
                    .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado (id " + req.clienteId() + ")"));
        }
        // Cadastro na hora, só com o nome: nada de exigir ficha completa no meio da venda
        if (req.clienteNome() != null && !req.clienteNome().isBlank()) {
            Cliente novo = new Cliente();
            novo.setNome(req.clienteNome().trim());
            novo.setTelefone(req.clienteTelefone());
            return clienteRepository.save(novo);
        }
        return null;
    }

    private VendaResumo montarResumo(Venda venda) {
        var itens = venda.getItens().stream()
                .map(i -> new VendaResumo.Item(
                        descricao(i.getVariacao()), i.getQuantidade(), i.getPrecoUnit(), i.getSubtotal()))
                .toList();

        Cliente cliente = venda.getCliente();
        BigDecimal saldoDevedor = venda.getFormaPagamento() == FormaPagamento.FIADO
                ? clienteRepository.saldoDevedor(cliente.getId())
                : null;

        return new VendaResumo(venda.getId(), venda.getData(), venda.getFormaPagamento(), venda.getTotal(),
                cliente != null ? cliente.getNome() : null, saldoDevedor, itens);
    }

    private String descricao(Variacao v) {
        StringJoiner sj = new StringJoiner(" ");
        sj.add(v.getProduto().getNome());
        if (v.getTamanho() != null) sj.add(v.getTamanho());
        if (v.getCor() != null) sj.add(v.getCor());
        return sj.toString();
    }
}
