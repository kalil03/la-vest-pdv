package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.repository.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Baixa de fiado por incobrabilidade — REVERSÍVEL. Espelha o recebimento do
 * carnê: zera o valor_aberto das parcelas em aberto do cliente e cria um
 * PagamentoFiado tipo BAIXA (positivo) que reduz o saldo. A BAIXA é excluída do
 * caixa/recebido e do prazo médio (não é dinheiro). Cada parcela zerada fica
 * guardada em baixa_fiado_item para o RESTAURAR devolver tudo idêntico.
 * Invariante mantido nos dois sentidos: SUM(valor_aberto) == saldo.
 */
@Service
public class BaixaService {

    private final BaixaFiadoRepository baixaRepo;
    private final PagamentoFiadoRepository pagamentoRepo;
    private final ParcelaFiadoRepository parcelaRepo;
    private final ClienteRepository clienteRepo;
    private final NamedParameterJdbcTemplate jdbc;

    public BaixaService(BaixaFiadoRepository baixaRepo, PagamentoFiadoRepository pagamentoRepo,
                        ParcelaFiadoRepository parcelaRepo, ClienteRepository clienteRepo,
                        NamedParameterJdbcTemplate jdbc) {
        this.baixaRepo = baixaRepo;
        this.pagamentoRepo = pagamentoRepo;
        this.parcelaRepo = parcelaRepo;
        this.clienteRepo = clienteRepo;
        this.jdbc = jdbc;
    }

    public record BaixaDTO(Long id, Long clienteId, String clienteNome, Instant data, String operador,
                           String motivo, BigDecimal valor, String status, Instant dataReversao,
                           String operadorReversao) {}

    public record DarBaixaRequest(Long clienteId, String motivo, String operador) {}

    /** Dá baixa em todo o saldo em aberto do cliente. Reversível via restaurar(). */
    @Transactional
    public BaixaDTO darBaixa(DarBaixaRequest req) {
        if (req.clienteId() == null) throw new RegraNegocioException("Cliente é obrigatório");
        Cliente cliente = clienteRepo.findById(req.clienteId())
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));

        BaixaFiado baixa = new BaixaFiado();
        baixa.setClienteId(req.clienteId());
        baixa.setOperador(req.operador());
        baixa.setMotivo(req.motivo() == null || req.motivo().isBlank() ? null : req.motivo().trim());

        BigDecimal total = BigDecimal.ZERO;

        // carnê migrado do SET (DEBITO_INICIAL)
        for (PagamentoFiado p : pagamentoRepo.findByClienteIdAndTipoOrderByDataAsc(
                req.clienteId(), TipoPagamentoFiado.DEBITO_INICIAL)) {
            BigDecimal ab = p.getValorAberto();
            if (ab == null || ab.signum() <= 0) continue;
            baixa.getItens().add(new BaixaFiadoItem(baixa, "L", p.getId(), ab));
            p.setValorAberto(BigDecimal.ZERO);
            total = total.add(ab);
        }
        // parcelas das vendas fiado
        for (ParcelaFiado pf : parcelaRepo.doCliente(req.clienteId())) {
            BigDecimal ab = pf.getValorAberto();
            if (ab == null || ab.signum() <= 0) continue;
            baixa.getItens().add(new BaixaFiadoItem(baixa, "V", pf.getId(), ab));
            pf.setValorAberto(BigDecimal.ZERO);
            total = total.add(ab);
        }

        if (total.signum() <= 0) {
            throw new RegraNegocioException("Este cliente não tem saldo em aberto para dar baixa");
        }
        baixa.setValor(total);

        // PagamentoFiado BAIXA: reduz o saldo, mas é excluído do caixa/recebido
        PagamentoFiado pag = new PagamentoFiado();
        pag.setCliente(cliente);
        pag.setValor(total);
        pag.setTipo(TipoPagamentoFiado.BAIXA);
        pag.setDetalhe("Baixa por incobrabilidade"
                + (baixa.getMotivo() != null ? " — " + baixa.getMotivo() : ""));
        pagamentoRepo.saveAndFlush(pag);

        baixa.setPagamentoId(pag.getId());
        baixaRepo.save(baixa);
        return toDTO(baixa, cliente.getNome());
    }

    /** Restaura uma baixa: devolve o valor_aberto das parcelas e remove o pagamento BAIXA. */
    @Transactional
    public void restaurar(Long baixaId, String operador) {
        BaixaFiado b = baixaRepo.findById(baixaId)
                .orElseThrow(() -> new RegraNegocioException("Baixa não encontrada (id " + baixaId + ")"));
        if (!"ATIVA".equals(b.getStatus())) {
            throw new RegraNegocioException("Esta baixa já foi revertida");
        }
        for (BaixaFiadoItem it : b.getItens()) {
            if ("L".equals(it.getOrigem())) {
                PagamentoFiado p = pagamentoRepo.findById(it.getRefId())
                        .orElseThrow(() -> new RegraNegocioException("Parcela do carnê sumiu — não dá para restaurar"));
                p.setValorAberto(nz(p.getValorAberto()).add(it.getValor()));
            } else {
                ParcelaFiado pf = parcelaRepo.findById(it.getRefId())
                        .orElseThrow(() -> new RegraNegocioException("Parcela da venda sumiu — não dá para restaurar"));
                pf.setValorAberto(nz(pf.getValorAberto()).add(it.getValor()));
            }
        }
        // solta a FK antes de apagar o pagamento BAIXA, senão o delete viola o vínculo
        Long pagId = b.getPagamentoId();
        b.setPagamentoId(null);
        b.setStatus("REVERTIDA");
        b.setDataReversao(Instant.now());
        b.setOperadorReversao(operador);
        baixaRepo.saveAndFlush(b);
        if (pagId != null) {
            pagamentoRepo.deleteById(pagId);
        }
    }

    @Transactional(readOnly = true)
    public List<BaixaDTO> listar(String status) {
        boolean filtra = "ATIVA".equals(status) || "REVERTIDA".equals(status);
        var params = new MapSqlParameterSource().addValue("status", status);
        String sql = "SELECT b.id, b.cliente_id, c.nome AS cliente_nome, b.data, b.operador, b.motivo, "
                + "b.valor, b.status, b.data_reversao, b.operador_reversao "
                + "FROM baixa_fiado b JOIN cliente c ON c.id = b.cliente_id "
                + (filtra ? "WHERE b.status = :status " : "")
                + "ORDER BY b.data DESC";
        return jdbc.query(sql, params, (rs, i) -> new BaixaDTO(
                rs.getLong("id"), rs.getLong("cliente_id"), rs.getString("cliente_nome"),
                rs.getTimestamp("data").toInstant(), rs.getString("operador"), rs.getString("motivo"),
                rs.getBigDecimal("valor"), rs.getString("status"),
                rs.getTimestamp("data_reversao") == null ? null : rs.getTimestamp("data_reversao").toInstant(),
                rs.getString("operador_reversao")));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BaixaDTO toDTO(BaixaFiado b, String clienteNome) {
        return new BaixaDTO(b.getId(), b.getClienteId(), clienteNome, b.getData(), b.getOperador(),
                b.getMotivo(), b.getValor(), b.getStatus(), b.getDataReversao(), b.getOperadorReversao());
    }
}
