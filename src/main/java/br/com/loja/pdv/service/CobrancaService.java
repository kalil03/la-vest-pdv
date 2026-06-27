package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.ContatoCobranca;
import br.com.loja.pdv.repository.ContatoCobrancaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Régua de cobrança ATIVA: a "Contas a Receber" lista parcela a parcela
 * (reativa); aqui a visão é POR CLIENTE em atraso, com o histórico de contatos
 * e a promessa de pagamento — uma lista de trabalho para cobrar e dar
 * follow-up em quem prometeu e não cumpriu. Tudo calculado de valor_aberto
 * (regra de ouro nº 1): a dívida nunca é gravada. Junta as duas origens do
 * crediário (carnê migrado do SET em pagamento_fiado.DEBITO_INICIAL + parcelas
 * das vendas fiado).
 */
@Service
public class CobrancaService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ContatoCobrancaRepository contatoRepo;

    public CobrancaService(NamedParameterJdbcTemplate jdbc, ContatoCobrancaRepository contatoRepo) {
        this.jdbc = jdbc;
        this.contatoRepo = contatoRepo;
    }

    public record Devedor(Long clienteId, String nome, String telefone,
                          BigDecimal totalAberto, BigDecimal totalVencido,
                          long parcelasVencidas, LocalDate vencimentoMaisAntigo, int diasAtraso,
                          Instant ultimoContato, String ultimoCanal, String ultimoResultado,
                          LocalDate promessaData) {}

    public record Totais(long devedores, BigDecimal totalVencido, BigDecimal totalAberto) {}

    public record Resultado(List<Devedor> devedores, Totais totais) {}

    public record Contato(Long id, Instant data, String operador, String canal,
                          String resultado, LocalDate promessaData, String observacao) {}

    public record RegistrarContatoRequest(Long clienteId, String canal, String resultado,
                                          LocalDate promessaData, String observacao, String operador) {}

    /** Cada parcela em aberto, das duas origens, já com telefone preferido do cliente. */
    private static final String FONTE = """
            SELECT c.id AS cliente_id, c.nome AS cliente_nome,
                   COALESCE(NULLIF(c.whats_fone1, ''), NULLIF(c.telefone, ''), NULLIF(c.fone2, '')) AS telefone,
                   CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS vencimento,
                   COALESCE(p.valor_aberto, 0) AS valor_aberto, p.tipo_notinha
            FROM pagamento_fiado p
            JOIN cliente c ON c.id = p.cliente_id
            WHERE p.tipo = 'DEBITO_INICIAL'
            UNION ALL
            SELECT c.id, c.nome,
                   COALESCE(NULLIF(c.whats_fone1, ''), NULLIF(c.telefone, ''), NULLIF(c.fone2, '')),
                   pf.vencimento, pf.valor_aberto, v.tipo_notinha
            FROM parcela_fiado pf
            JOIN venda v ON v.id = pf.venda_id
            JOIN cliente c ON c.id = v.cliente_id
            """;

    /** Agrupa por cliente; só entram os que têm parcela vencida em aberto. Filtra por tipo da notinha. */
    private static final String AGRUPADO = """
            SELECT cliente_id, cliente_nome, MAX(telefone) AS telefone,
                   SUM(valor_aberto) FILTER (WHERE valor_aberto > 0) AS total_aberto,
                   SUM(valor_aberto) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) AS total_vencido,
                   COUNT(*) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) AS parcelas_vencidas,
                   MIN(vencimento) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) AS venc_mais_antigo
            FROM (""" + FONTE + """
            ) t
            WHERE (:tipo = '' OR t.tipo_notinha = :tipo)
            GROUP BY cliente_id, cliente_nome
            HAVING SUM(valor_aberto) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) > 0
            """;

    @Transactional(readOnly = true)
    public Resultado listar(String q, String ordenar, String tipo) {
        var params = new MapSqlParameterSource()
                .addValue("q", q == null ? "" : q.trim())
                .addValue("tipo", tipo == null ? "" : tipo.trim());

        // a mais antiga primeiro = mais urgente; promessa = quem prometeu (vencidas no topo);
        // default mostra o maior valor vencido no topo
        String ordem = switch (ordenar == null ? "" : ordenar) {
            case "atraso" -> "venc_mais_antigo ASC NULLS LAST";
            case "promessa" -> "promessa_data ASC NULLS LAST";
            case "nome" -> "cliente_nome ASC";
            default -> "total_vencido DESC";
        };

        String sql = "SELECT g.*, uc.ultimo_data, uc.ultimo_canal, uc.ultimo_resultado, pr.promessa_data "
                + "FROM (" + AGRUPADO + ") g "
                + "LEFT JOIN LATERAL (SELECT data AS ultimo_data, canal AS ultimo_canal, resultado AS ultimo_resultado "
                + "  FROM contato_cobranca cc WHERE cc.cliente_id = g.cliente_id ORDER BY cc.data DESC LIMIT 1) uc ON true "
                + "LEFT JOIN LATERAL (SELECT MAX(promessa_data) AS promessa_data "
                + "  FROM contato_cobranca cc WHERE cc.cliente_id = g.cliente_id AND cc.resultado = 'PROMETEU') pr ON true "
                + "WHERE (:q = '' OR unaccent(g.cliente_nome) ILIKE unaccent('%' || :q || '%') "
                + "       OR g.telefone ILIKE '%' || :q || '%') "
                + "ORDER BY " + ordem + " LIMIT 1000";

        LocalDate hoje = LocalDate.now();
        List<Devedor> devedores = jdbc.query(sql, params, (rs, i) -> {
            LocalDate venc = rs.getDate("venc_mais_antigo").toLocalDate();
            int dias = (int) ChronoUnit.DAYS.between(venc, hoje);
            java.sql.Timestamp uc = rs.getTimestamp("ultimo_data");
            java.sql.Date pr = rs.getDate("promessa_data");
            return new Devedor(rs.getLong("cliente_id"), rs.getString("cliente_nome"),
                    rs.getString("telefone"), rs.getBigDecimal("total_aberto"),
                    rs.getBigDecimal("total_vencido"), rs.getLong("parcelas_vencidas"),
                    venc, dias,
                    uc == null ? null : uc.toInstant(), rs.getString("ultimo_canal"),
                    rs.getString("ultimo_resultado"), pr == null ? null : pr.toLocalDate());
        });

        // totais (respeitam o tipo selecionado, mas não a busca por nome) — retrato da carteira
        Totais totais = jdbc.queryForObject(
                "SELECT COUNT(*) AS devedores, COALESCE(SUM(total_vencido), 0) AS total_vencido, "
                        + "COALESCE(SUM(total_aberto), 0) AS total_aberto FROM (" + AGRUPADO + ") g",
                new MapSqlParameterSource().addValue("tipo", tipo == null ? "" : tipo.trim()),
                (rs, i) -> new Totais(rs.getLong("devedores"),
                        rs.getBigDecimal("total_vencido"), rs.getBigDecimal("total_aberto")));

        return new Resultado(devedores, totais);
    }

    @Transactional
    public void registrarContato(RegistrarContatoRequest req) {
        if (req.clienteId() == null) throw new RegraNegocioException("Cliente é obrigatório");
        if (req.canal() == null || req.canal().isBlank()) throw new RegraNegocioException("Canal é obrigatório");
        if (req.resultado() == null || req.resultado().isBlank()) throw new RegraNegocioException("Resultado é obrigatório");

        ContatoCobranca c = new ContatoCobranca();
        c.setClienteId(req.clienteId());
        c.setCanal(req.canal());
        c.setResultado(req.resultado());
        // só guarda promessa quando o resultado é "prometeu pagar"
        c.setPromessaData("PROMETEU".equals(req.resultado()) ? req.promessaData() : null);
        c.setObservacao(req.observacao() == null || req.observacao().isBlank() ? null : req.observacao().trim());
        c.setOperador(req.operador());
        contatoRepo.save(c);
    }

    @Transactional(readOnly = true)
    public List<Contato> listarContatos(Long clienteId) {
        return contatoRepo.findByClienteIdOrderByDataDesc(clienteId).stream()
                .map(c -> new Contato(c.getId(), c.getData(), c.getOperador(), c.getCanal(),
                        c.getResultado(), c.getPromessaData(), c.getObservacao()))
                .toList();
    }
}
