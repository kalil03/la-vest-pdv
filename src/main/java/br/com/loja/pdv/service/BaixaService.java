package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.repository.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final RecebimentoItemRepository recebimentoItemRepo;
    private final NamedParameterJdbcTemplate jdbc;

    public BaixaService(BaixaFiadoRepository baixaRepo, PagamentoFiadoRepository pagamentoRepo,
                        ParcelaFiadoRepository parcelaRepo, ClienteRepository clienteRepo,
                        RecebimentoItemRepository recebimentoItemRepo,
                        NamedParameterJdbcTemplate jdbc) {
        this.baixaRepo = baixaRepo;
        this.pagamentoRepo = pagamentoRepo;
        this.parcelaRepo = parcelaRepo;
        this.clienteRepo = clienteRepo;
        this.recebimentoItemRepo = recebimentoItemRepo;
        this.jdbc = jdbc;
    }

    public record BaixaDTO(Long id, Long clienteId, String clienteNome, Instant data, String operador,
                           String motivo, BigDecimal valor, String status, Instant dataReversao,
                           String operadorReversao) {}

    public record DarBaixaRequest(Long clienteId, String motivo, String operador) {}

    /**
     * Baixa/ajuste de UMA notinha (conjunto de parcelas), para a conferência da
     * gaveta. refs vêm da tela como "V123"/"L45" (id da parcela_fiado / do
     * DEBITO_INICIAL). manterAberto = quanto ainda falta de verdade: null ou 0
     * baixa a notinha inteira; um valor menor que o saldo baixa só a diferença
     * (das parcelas mais antigas primeiro). É a mesma BaixaFiado reversível.
     */
    public record BaixaNotinhaRequest(List<String> refs, BigDecimal manterAberto,
                                      String operador, String motivo) {}

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

    /** Uma parcela resolvida de uma notinha, com o que falta e o vencimento (para o FIFO). */
    private record Linha(String origem, Long id, BigDecimal aberto, LocalDate venc, Long clienteId) {}

    /** Baixa (total) ou ajuste (parcial) de uma notinha. Reversível via restaurar(). */
    @Transactional
    public BaixaDTO baixarNotinha(BaixaNotinhaRequest req) {
        if (req.refs() == null || req.refs().isEmpty()) {
            throw new RegraNegocioException("Nenhuma notinha selecionada");
        }

        List<Linha> linhas = new ArrayList<>();
        for (String ref : req.refs()) {
            if (ref == null || ref.length() < 2) throw new RegraNegocioException("Referência inválida: " + ref);
            String origem = ref.substring(0, 1);
            Long id = Long.valueOf(ref.substring(1));
            if ("V".equals(origem)) {
                ParcelaFiado pf = parcelaRepo.findById(id)
                        .orElseThrow(() -> new RegraNegocioException("Parcela " + ref + " não encontrada"));
                linhas.add(new Linha("V", id, nz(pf.getValorAberto()), pf.getVencimento(),
                        pf.getVenda().getCliente().getId()));
            } else if ("L".equals(origem)) {
                PagamentoFiado p = pagamentoRepo.findById(id)
                        .orElseThrow(() -> new RegraNegocioException("Lançamento " + ref + " não encontrado"));
                linhas.add(new Linha("L", id, nz(p.getValorAberto()),
                        LocalDate.ofInstant(p.getData(), br.com.loja.pdv.Fuso.LOJA),
                        p.getCliente().getId()));
            } else {
                throw new RegraNegocioException("Referência inválida: " + ref);
            }
        }

        Long clienteId = linhas.get(0).clienteId();
        if (linhas.stream().anyMatch(l -> !clienteId.equals(l.clienteId()))) {
            throw new RegraNegocioException("A notinha mistura clientes diferentes");
        }
        Cliente cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado"));

        BigDecimal totalAberto = linhas.stream().map(Linha::aberto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAberto.signum() <= 0) {
            throw new RegraNegocioException("Esta notinha não tem saldo em aberto");
        }

        BigDecimal manter = req.manterAberto() == null ? BigDecimal.ZERO : req.manterAberto();
        if (manter.signum() < 0) manter = BigDecimal.ZERO;
        if (manter.compareTo(totalAberto) >= 0) {
            throw new RegraNegocioException("O valor a manter é igual ou maior que o saldo atual da notinha ("
                    + totalAberto + "). Para baixar tudo use \"Dar baixa\"; para aumentar a dívida use o carnê/venda.");
        }
        BigDecimal aBaixar = totalAberto.subtract(manter);

        // FIFO: baixa/ajusta das parcelas mais antigas primeiro
        linhas.sort(Comparator.comparing(Linha::venc).thenComparing(Linha::id));

        BaixaFiado baixa = new BaixaFiado();
        baixa.setClienteId(clienteId);
        baixa.setOperador(req.operador());
        baixa.setMotivo(req.motivo() == null || req.motivo().isBlank() ? null : req.motivo().trim());

        BigDecimal restante = aBaixar;
        BigDecimal baixado = BigDecimal.ZERO;
        for (Linha l : linhas) {
            if (restante.signum() <= 0) break;
            BigDecimal tira = l.aberto().min(restante);
            if (tira.signum() <= 0) continue;
            if ("V".equals(l.origem())) {
                ParcelaFiado pf = parcelaRepo.findById(l.id()).orElseThrow();
                pf.setValorAberto(nz(pf.getValorAberto()).subtract(tira));
            } else {
                PagamentoFiado p = pagamentoRepo.findById(l.id()).orElseThrow();
                p.setValorAberto(nz(p.getValorAberto()).subtract(tira));
            }
            baixa.getItens().add(new BaixaFiadoItem(baixa, l.origem(), l.id(), tira));
            restante = restante.subtract(tira);
            baixado = baixado.add(tira);
        }
        baixa.setValor(baixado);

        boolean ajuste = manter.signum() > 0;
        PagamentoFiado pag = new PagamentoFiado();
        pag.setCliente(cliente);
        pag.setValor(baixado);
        pag.setTipo(TipoPagamentoFiado.BAIXA);
        pag.setDetalhe((ajuste ? "Ajuste de saldo (conferência da gaveta)" : "Baixa (conferência da gaveta)")
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

    // ---------- reversão de RECEBIMENTO de carnê (dinheiro lançado errado) ----------

    public record RecebimentoDTO(Long id, String clienteNome, String tipo, BigDecimal valor,
                                 Instant data, String operador) {}

    /**
     * Recebimentos de carnê reversíveis (têm o detalhamento por parcela, gravado
     * a partir da V25), dos últimos N dias. Recebimento antigo não aparece aqui —
     * não dá para reverter automaticamente sem saber quanto foi em cada parcela.
     */
    @Transactional(readOnly = true)
    public List<RecebimentoDTO> listarRecebimentos(int dias) {
        var params = new MapSqlParameterSource().addValue("desde",
                LocalDate.now(br.com.loja.pdv.Fuso.LOJA).minusDays(Math.max(1, dias)));
        // um recebimento misto tem pagamentos-irmãos (mesma pai): o total é a soma
        // deles, e a forma vira "MISTO". Lista só os PAIS (quem tem o detalhamento).
        String sql = "SELECT p.id, c.nome AS cliente_nome, p.data, v.nome AS vendedor, "
                + "  CASE WHEN EXISTS (SELECT 1 FROM pagamento_fiado s WHERE s.pagamento_pai_id = p.id) "
                + "       THEN 'MISTO' ELSE p.tipo END AS tipo, "
                + "  p.valor + COALESCE((SELECT SUM(s.valor) FROM pagamento_fiado s WHERE s.pagamento_pai_id = p.id), 0) AS valor "
                + "FROM pagamento_fiado p "
                + "JOIN cliente c ON c.id = p.cliente_id "
                + "LEFT JOIN vendedor v ON v.id = p.vendedor_id "
                + "WHERE p.tipo NOT IN ('DEBITO_INICIAL', 'BAIXA') "
                + "  AND p.pagamento_pai_id IS NULL "
                + "  AND EXISTS (SELECT 1 FROM recebimento_item ri WHERE ri.pagamento_id = p.id) "
                + "  AND CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) >= :desde "
                + "ORDER BY p.data DESC";
        return jdbc.query(sql, params, (rs, i) -> new RecebimentoDTO(
                rs.getLong("id"), rs.getString("cliente_nome"), rs.getString("tipo"),
                rs.getBigDecimal("valor"), rs.getTimestamp("data").toInstant(), rs.getString("vendedor")));
    }

    /**
     * Reverte um recebimento de carnê: devolve a cada parcela o valor que ela
     * recebeu e apaga o recebimento (some do caixa do dia em que entrou, como se
     * não tivesse acontecido). Só funciona para recebimentos com detalhamento.
     */
    @Transactional
    public void reverterRecebimento(Long pagamentoId, String operador) {
        PagamentoFiado pag = pagamentoRepo.findById(pagamentoId)
                .orElseThrow(() -> new RegraNegocioException("Recebimento não encontrado (id " + pagamentoId + ")"));
        if (pag.getTipo() == TipoPagamentoFiado.DEBITO_INICIAL || pag.getTipo() == TipoPagamentoFiado.BAIXA) {
            throw new RegraNegocioException("Este lançamento não é um recebimento de carnê");
        }
        if (pag.getPagamentoPaiId() != null) {
            throw new RegraNegocioException("Esta é uma das formas de um recebimento misto — "
                    + "reverta pelo recebimento principal na lista");
        }
        List<RecebimentoItem> itens = recebimentoItemRepo.findByPagamentoId(pagamentoId);
        if (itens.isEmpty()) {
            throw new RegraNegocioException("Recebimento antigo, sem o detalhamento por parcela — "
                    + "não dá para reverter automaticamente. Ajuste manualmente.");
        }
        for (RecebimentoItem it : itens) {
            if ("L".equals(it.getOrigem())) {
                PagamentoFiado p = pagamentoRepo.findById(it.getRefId())
                        .orElseThrow(() -> new RegraNegocioException("Parcela do carnê sumiu — não dá para reverter"));
                p.setValorAberto(nz(p.getValorAberto()).add(it.getValor()));
            } else {
                ParcelaFiado pf = parcelaRepo.findById(it.getRefId())
                        .orElseThrow(() -> new RegraNegocioException("Parcela da venda sumiu — não dá para reverter"));
                pf.setValorAberto(nz(pf.getValorAberto()).add(it.getValor()));
            }
        }
        recebimentoItemRepo.deleteByPagamentoId(pagamentoId);
        pagamentoRepo.deleteByPagamentoPaiId(pagamentoId); // formas-irmãs do recebimento misto
        pagamentoRepo.deleteById(pagamentoId);
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

    /** valor = o que a baixa tirou desta nota; restante = o que AINDA falta nela hoje. */
    public record NotaBaixada(String descricao, BigDecimal valor, BigDecimal restante) {}
    public record ComprovanteBaixa(Long baixaId, String clienteNome, Instant data, String operador,
                                   BigDecimal total, BigDecimal saldoRestante, List<NotaBaixada> notas) {}

    /**
     * Dados para reimprimir a promissória com o saldo ATUALIZADO após a baixa —
     * o operador grampeia na nota física do SET em vez de marcar à mão. Lista o
     * que a baixa tirou de cada nota e o que sobrou nela, mais o saldo do cliente.
     * Baixa parcial (ajuste) e cliente com outras notinhas em aberto NÃO ficam
     * zerados: o cupom tem que dizer a verdade, é ele que vai para a gaveta.
     */
    @Transactional(readOnly = true)
    public ComprovanteBaixa comprovante(Long baixaId) {
        BaixaFiado b = baixaRepo.findById(baixaId)
                .orElseThrow(() -> new RegraNegocioException("Baixa não encontrada (id " + baixaId + ")"));
        String clienteNome = clienteRepo.findById(b.getClienteId())
                .map(Cliente::getNome).orElse("Cliente");
        List<NotaBaixada> notas = new java.util.ArrayList<>();
        for (BaixaFiadoItem it : b.getItens()) {
            notas.add(new NotaBaixada(descreverNota(it), it.getValor(), abertoAtual(it)));
        }
        return new ComprovanteBaixa(b.getId(), clienteNome, b.getData(), b.getOperador(), b.getValor(),
                nz(clienteRepo.saldoDevedor(b.getClienteId())), notas);
    }

    /** O que ainda falta na nota AGORA (0 = quitada). */
    private BigDecimal abertoAtual(BaixaFiadoItem it) {
        String tabela = "L".equals(it.getOrigem()) ? "pagamento_fiado" : "parcela_fiado";
        var v = jdbc.query("SELECT valor_aberto FROM " + tabela + " WHERE id = :id",
                new MapSqlParameterSource("id", it.getRefId()), (rs, i) -> rs.getBigDecimal(1));
        return v.isEmpty() ? BigDecimal.ZERO : nz(v.get(0));
    }

    private String descreverNota(BaixaFiadoItem it) {
        var p = new MapSqlParameterSource("id", it.getRefId());
        if ("L".equals(it.getOrigem())) {
            var doc = jdbc.query("SELECT documento FROM pagamento_fiado WHERE id = :id", p,
                    (rs, i) -> rs.getString(1));
            String d = doc.isEmpty() ? null : doc.get(0);
            return "Nota do carnê (SET)" + (d != null && !d.isBlank() ? " nº " + d : "");
        }
        var linha = jdbc.query("SELECT venda_id, numero FROM parcela_fiado WHERE id = :id", p,
                (rs, i) -> "Venda nº " + rs.getLong("venda_id") + " — " + rs.getInt("numero") + "ª parcela");
        return linha.isEmpty() ? "Parcela de venda" : linha.get(0);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private BaixaDTO toDTO(BaixaFiado b, String clienteNome) {
        return new BaixaDTO(b.getId(), b.getClienteId(), clienteNome, b.getData(), b.getOperador(),
                b.getMotivo(), b.getValor(), b.getStatus(), b.getDataReversao(), b.getOperadorReversao());
    }
}
