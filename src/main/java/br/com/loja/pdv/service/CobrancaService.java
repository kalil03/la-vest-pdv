package br.com.loja.pdv.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Régua de cobrança: a "Contas a Receber" lista parcela a parcela (reativa);
 * aqui a visão é POR CLIENTE em atraso — uma lista de trabalho para ligar/mandar
 * mensagem e cobrar. Tudo calculado de valor_aberto (regra de ouro nº 1): só
 * leitura, nenhuma dívida gravada. Junta as duas origens do crediário (carnê
 * migrado do SET em pagamento_fiado.DEBITO_INICIAL + parcelas das vendas fiado).
 */
@Service
public class CobrancaService {

    private final NamedParameterJdbcTemplate jdbc;

    public CobrancaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Devedor(Long clienteId, String nome, String telefone,
                          BigDecimal totalAberto, BigDecimal totalVencido,
                          long parcelasVencidas, LocalDate vencimentoMaisAntigo, int diasAtraso) {}

    public record Totais(long devedores, BigDecimal totalVencido, BigDecimal totalAberto) {}

    public record Resultado(List<Devedor> devedores, Totais totais) {}

    /** Cada parcela em aberto, das duas origens, já com telefone preferido do cliente. */
    private static final String FONTE = """
            SELECT c.id AS cliente_id, c.nome AS cliente_nome,
                   COALESCE(NULLIF(c.whats_fone1, ''), NULLIF(c.telefone, ''), NULLIF(c.fone2, '')) AS telefone,
                   CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS vencimento,
                   COALESCE(p.valor_aberto, 0) AS valor_aberto
            FROM pagamento_fiado p
            JOIN cliente c ON c.id = p.cliente_id
            WHERE p.tipo = 'DEBITO_INICIAL'
            UNION ALL
            SELECT c.id, c.nome,
                   COALESCE(NULLIF(c.whats_fone1, ''), NULLIF(c.telefone, ''), NULLIF(c.fone2, '')),
                   pf.vencimento, pf.valor_aberto
            FROM parcela_fiado pf
            JOIN venda v ON v.id = pf.venda_id
            JOIN cliente c ON c.id = v.cliente_id
            """;

    /** Agrupa por cliente; só entram os que têm parcela vencida em aberto. */
    private static final String AGRUPADO = """
            SELECT cliente_id, cliente_nome, MAX(telefone) AS telefone,
                   SUM(valor_aberto) FILTER (WHERE valor_aberto > 0) AS total_aberto,
                   SUM(valor_aberto) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) AS total_vencido,
                   COUNT(*) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) AS parcelas_vencidas,
                   MIN(vencimento) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) AS venc_mais_antigo
            FROM (""" + FONTE + """
            ) t
            GROUP BY cliente_id, cliente_nome
            HAVING SUM(valor_aberto) FILTER (WHERE valor_aberto > 0 AND vencimento < CURRENT_DATE) > 0
            """;

    @Transactional(readOnly = true)
    public Resultado listar(String q, String ordenar) {
        var params = new MapSqlParameterSource().addValue("q", q == null ? "" : q.trim());

        // a mais antiga primeiro = mais urgente; default mostra o maior valor vencido no topo
        String ordem = switch (ordenar == null ? "" : ordenar) {
            case "atraso" -> "venc_mais_antigo ASC NULLS LAST";
            case "nome" -> "cliente_nome ASC";
            default -> "total_vencido DESC";
        };

        String sql = "SELECT * FROM (" + AGRUPADO + ") g "
                + "WHERE (:q = '' OR unaccent(g.cliente_nome) ILIKE unaccent('%' || :q || '%') "
                + "       OR g.telefone ILIKE '%' || :q || '%') "
                + "ORDER BY " + ordem + " LIMIT 1000";

        LocalDate hoje = LocalDate.now();
        List<Devedor> devedores = jdbc.query(sql, params, (rs, i) -> {
            LocalDate venc = rs.getDate("venc_mais_antigo").toLocalDate();
            int dias = (int) ChronoUnit.DAYS.between(venc, hoje);
            return new Devedor(rs.getLong("cliente_id"), rs.getString("cliente_nome"),
                    rs.getString("telefone"), rs.getBigDecimal("total_aberto"),
                    rs.getBigDecimal("total_vencido"), rs.getLong("parcelas_vencidas"),
                    venc, dias);
        });

        // totais globais (independem do filtro) — retrato da inadimplência inteira
        Totais totais = jdbc.queryForObject(
                "SELECT COUNT(*) AS devedores, COALESCE(SUM(total_vencido), 0) AS total_vencido, "
                        + "COALESCE(SUM(total_aberto), 0) AS total_aberto FROM (" + AGRUPADO + ") g",
                new MapSqlParameterSource(),
                (rs, i) -> new Totais(rs.getLong("devedores"),
                        rs.getBigDecimal("total_vencido"), rs.getBigDecimal("total_aberto")));

        return new Resultado(devedores, totais);
    }
}
