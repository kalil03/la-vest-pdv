package br.com.loja.pdv.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Contas a receber: visão única de TODAS as parcelas (carnê migrado do SET +
 * parcelas das vendas fiado), com status calculado a partir de valor_aberto —
 * nada de flag gravada (regra de ouro nº 1). Pagas pelo sistema aparecem como
 * QUITADA; o histórico pago ainda no SET não foi migrado.
 */
@Service
public class ContasReceberService {

    private final NamedParameterJdbcTemplate jdbc;

    public ContasReceberService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Conta(String id, Long clienteId, String clienteNome, Long notinha,
                        String descricao, LocalDate vencimento,
                        BigDecimal valor, BigDecimal valorAberto, String status) {}

    public record Totais(BigDecimal totalAberto, BigDecimal totalVencido,
                         long parcelasAbertas, BigDecimal recebidoMes) {}

    public record Pagina(List<Conta> contas, long total, int pagina, int porPagina, Totais totais) {}

    /** Todas as parcelas, das duas origens, já com valor em aberto. */
    private static final String FONTE = """
            SELECT 'L' || p.id AS id, c.id AS cliente_id, c.nome AS cliente_nome,
                   NULL::bigint AS notinha, 'Carnê SET' AS descricao,
                   CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS vencimento,
                   -p.valor AS valor, COALESCE(p.valor_aberto, 0) AS valor_aberto
            FROM pagamento_fiado p
            JOIN cliente c ON c.id = p.cliente_id
            WHERE p.tipo = 'DEBITO_INICIAL'
            UNION ALL
            SELECT 'V' || pf.id, c.id, c.nome,
                   v.id, 'Parcela ' || pf.numero || '/' || cnt.total,
                   pf.vencimento, pf.valor, pf.valor_aberto
            FROM parcela_fiado pf
            JOIN venda v ON v.id = pf.venda_id
            JOIN cliente c ON c.id = v.cliente_id
            JOIN LATERAL (SELECT COUNT(*) AS total FROM parcela_fiado x WHERE x.venda_id = v.id) cnt ON true
            """;

    @Transactional(readOnly = true)
    public Pagina listar(String q, String status, LocalDate de, LocalDate ate, int pagina) {
        int porPagina = 50;
        var params = new MapSqlParameterSource()
                .addValue("q", q == null ? "" : q.trim())
                .addValue("de", de)
                .addValue("ate", ate)
                .addValue("limite", porPagina)
                .addValue("offset", Math.max(0, pagina - 1) * porPagina);

        String filtro = """
                WHERE (:q = '' OR unaccent(t.cliente_nome) ILIKE unaccent('%' || :q || '%')
                       OR CAST(t.notinha AS text) = :q)
                  AND (CAST(:de AS date) IS NULL OR t.vencimento >= :de)
                  AND (CAST(:ate AS date) IS NULL OR t.vencimento <= :ate)
                """ + condicaoStatus(status);

        List<Conta> contas = jdbc.query(
                "SELECT * FROM (" + FONTE + ") t " + filtro
                        + " ORDER BY t.vencimento, t.id LIMIT :limite OFFSET :offset",
                params,
                (rs, i) -> {
                    BigDecimal valor = rs.getBigDecimal("valor");
                    BigDecimal aberto = rs.getBigDecimal("valor_aberto");
                    return new Conta(rs.getString("id"), rs.getLong("cliente_id"),
                            rs.getString("cliente_nome"),
                            rs.getObject("notinha") == null ? null : rs.getLong("notinha"),
                            rs.getString("descricao"), rs.getDate("vencimento").toLocalDate(),
                            valor, aberto, statusDe(valor, aberto, rs.getDate("vencimento").toLocalDate()));
                });

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + FONTE + ") t " + filtro, params, Long.class);

        return new Pagina(contas, total == null ? 0 : total, Math.max(1, pagina), porPagina, totais());
    }

    /** KPIs gerais (sem filtro): retrato do crediário inteiro. */
    private Totais totais() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(t.valor_aberto), 0) AS total_aberto,
                       COALESCE(SUM(t.valor_aberto) FILTER (WHERE t.vencimento < CURRENT_DATE), 0) AS total_vencido,
                       COUNT(*) FILTER (WHERE t.valor_aberto > 0) AS parcelas_abertas,
                       (SELECT COALESCE(SUM(p.valor), 0) FROM pagamento_fiado p
                        WHERE p.tipo <> 'DEBITO_INICIAL' AND p.valor > 0
                          AND date_trunc('month', p.data AT TIME ZONE 'America/Sao_Paulo')
                              = date_trunc('month', CURRENT_DATE)) AS recebido_mes
                FROM (""" + FONTE + ") t WHERE t.valor_aberto > 0",
                new MapSqlParameterSource(),
                (rs, i) -> new Totais(rs.getBigDecimal("total_aberto"), rs.getBigDecimal("total_vencido"),
                        rs.getLong("parcelas_abertas"), rs.getBigDecimal("recebido_mes")));
    }

    private String condicaoStatus(String status) {
        return switch (status == null ? "" : status) {
            case "ABERTA" -> " AND t.valor_aberto > 0";
            case "ATRASADA" -> " AND t.valor_aberto > 0 AND t.vencimento < CURRENT_DATE";
            case "PARCIAL" -> " AND t.valor_aberto > 0 AND t.valor_aberto < t.valor";
            case "QUITADA" -> " AND t.valor_aberto = 0";
            default -> "";
        };
    }

    private String statusDe(BigDecimal valor, BigDecimal aberto, LocalDate vencimento) {
        if (aberto.signum() == 0) return "QUITADA";
        if (vencimento.isBefore(LocalDate.now())) return "ATRASADA";
        if (aberto.compareTo(valor) < 0) return "PARCIAL";
        return "ABERTA";
    }
}
