package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.StringJoiner;

/**
 * Condicional: peças que a cliente leva para provar em casa. Por decisão do
 * dono, a saída NÃO baixa estoque — só o fechamento baixa, e ele reaproveita o
 * caixa: o front manda as peças que ficaram para a tela de venda, fecha uma
 * Venda normal e depois marca a condicional como FECHADA (ligando à venda).
 * O que volta não mexe em nada.
 */
@Service
public class CondicionalService {

    private final CondicionalRepository condicionalRepo;
    private final ClienteRepository clienteRepo;
    private final VendedorRepository vendedorRepo;
    private final VariacaoRepository variacaoRepo;

    public CondicionalService(CondicionalRepository condicionalRepo, ClienteRepository clienteRepo,
                              VendedorRepository vendedorRepo, VariacaoRepository variacaoRepo) {
        this.condicionalRepo = condicionalRepo;
        this.clienteRepo = clienteRepo;
        this.vendedorRepo = vendedorRepo;
        this.variacaoRepo = variacaoRepo;
    }

    public record NovoItem(Long variacaoId, int quantidade, BigDecimal precoUnit) {}

    public record AbrirRequest(Long clienteId, Long vendedorId, String observacao, List<NovoItem> itens) {}

    public record ItemDTO(Long variacaoId, String codigo, String descricao, int quantidade, BigDecimal precoUnit) {}

    public record CondicionalDTO(Long id, Long clienteId, String clienteNome, Long vendedorId, String vendedorNome,
                                 Instant dataSaida, String status, String observacao, Long vendaId,
                                 Instant dataFechamento, List<ItemDTO> itens, BigDecimal total, int totalPecas) {}

    public record Resumo(Long id, Long clienteId, String clienteNome, Instant dataSaida, String status,
                         int totalPecas, BigDecimal total, Long vendaId) {}

    @Transactional
    public CondicionalDTO abrir(AbrirRequest req) {
        if (req.clienteId() == null) throw new RegraNegocioException("A condicional precisa de um cliente");
        if (req.itens() == null || req.itens().isEmpty()) throw new RegraNegocioException("Adicione ao menos uma peça");

        Cliente cliente = clienteRepo.findById(req.clienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));

        Condicional c = new Condicional();
        c.setCliente(cliente);
        if (req.vendedorId() != null) {
            c.setVendedor(vendedorRepo.findById(req.vendedorId())
                    .orElseThrow(() -> new RegraNegocioException("Vendedor não encontrado")));
        }
        c.setObservacao(req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim());

        for (NovoItem ni : req.itens()) {
            if (ni.quantidade() <= 0) throw new RegraNegocioException("Quantidade inválida");
            Variacao v = variacaoRepo.findById(ni.variacaoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado (variação " + ni.variacaoId() + ")"));
            ItemCondicional ic = new ItemCondicional();
            ic.setCondicional(c);
            ic.setVariacao(v);
            ic.setQuantidade(ni.quantidade());
            ic.setPrecoUnit(ni.precoUnit() != null ? ni.precoUnit() : v.getProduto().getPreco());
            c.getItens().add(ic);
        }

        condicionalRepo.save(c);
        return toDTO(c);
    }

    @Transactional(readOnly = true)
    public List<Resumo> listar(String status) {
        List<Condicional> lista = (status == null || status.isBlank() || "TODAS".equals(status))
                ? condicionalRepo.findAllByOrderByDataSaidaDesc()
                : condicionalRepo.findByStatusOrderByDataSaidaDesc(status);
        return lista.stream().map(this::toResumo).toList();
    }

    @Transactional(readOnly = true)
    public CondicionalDTO buscar(Long id) {
        return toDTO(carregar(id));
    }

    /** Cancela: as peças voltaram, nada foi vendido. Só se ainda estiver ABERTA. */
    @Transactional
    public void cancelar(Long id) {
        Condicional c = carregar(id);
        exigirAberta(c);
        c.setStatus("CANCELADA");
        c.setDataFechamento(Instant.now());
    }

    /** Marca FECHADA ligando à venda gerada no caixa (peças que a cliente ficou). */
    @Transactional
    public void marcarFechada(Long id, Long vendaId) {
        Condicional c = carregar(id);
        exigirAberta(c);
        c.setStatus("FECHADA");
        c.setVendaId(vendaId);
        c.setDataFechamento(Instant.now());
    }

    private Condicional carregar(Long id) {
        return condicionalRepo.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Condicional não encontrada (id " + id + ")"));
    }

    private void exigirAberta(Condicional c) {
        if (!"ABERTA".equals(c.getStatus())) {
            throw new RegraNegocioException("Esta condicional já foi " + c.getStatus().toLowerCase());
        }
    }

    private CondicionalDTO toDTO(Condicional c) {
        var itens = c.getItens().stream()
                .map(i -> new ItemDTO(i.getVariacao().getId(), i.getVariacao().getProduto().getCodigo(),
                        descricao(i.getVariacao()), i.getQuantidade(), i.getPrecoUnit()))
                .toList();
        return new CondicionalDTO(c.getId(), c.getCliente().getId(), c.getCliente().getNome(),
                c.getVendedor() != null ? c.getVendedor().getId() : null,
                c.getVendedor() != null ? c.getVendedor().getNome() : null,
                c.getDataSaida(), c.getStatus(), c.getObservacao(), c.getVendaId(), c.getDataFechamento(),
                itens, total(c), totalPecas(c));
    }

    private Resumo toResumo(Condicional c) {
        return new Resumo(c.getId(), c.getCliente().getId(), c.getCliente().getNome(),
                c.getDataSaida(), c.getStatus(), totalPecas(c), total(c), c.getVendaId());
    }

    private BigDecimal total(Condicional c) {
        return c.getItens().stream()
                .map(i -> i.getPrecoUnit().multiply(BigDecimal.valueOf(i.getQuantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int totalPecas(Condicional c) {
        return c.getItens().stream().mapToInt(ItemCondicional::getQuantidade).sum();
    }

    private String descricao(Variacao v) {
        StringJoiner sj = new StringJoiner(" ");
        sj.add(v.getProduto().getNome());
        if (v.getTamanho() != null) sj.add(v.getTamanho());
        if (v.getCor() != null) sj.add(v.getCor());
        return sj.toString();
    }
}
