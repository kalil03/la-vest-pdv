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
                        String documento, String descricao, LocalDate vencimento,
                        BigDecimal valor, BigDecimal valorAberto, String status) {}

    public record Totais(BigDecimal totalAberto, BigDecimal totalVencido,
                         long parcelasAbertas, BigDecimal recebidoMes) {}

    public record Pagina(List<Conta> contas, long total, int pagina, int porPagina, Totais totais) {}

    /** Uma notinha em aberto, agrupando suas parcelas, para a conferência da gaveta. */
    public record NotinhaAberta(String chave, String origem, Long clienteId, String clienteNome,
                                String rotulo, String tipo, LocalDate vencimento,
                                BigDecimal totalAberto, List<String> refs) {}

    public record PaginaGaveta(List<NotinhaAberta> notinhas, long total, int pagina, int porPagina) {}

    /** Todas as parcelas, das duas origens, já com valor em aberto. */
    private static final String FONTE = """
            SELECT 'L' || p.id AS id, c.id AS cliente_id, c.nome AS cliente_nome,
                   NULL::bigint AS notinha, p.documento,
                   'Carnê SET' || COALESCE(' · ' || p.tipo_notinha, '') AS descricao,
                   CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS vencimento,
                   -p.valor AS valor, COALESCE(p.valor_aberto, 0) AS valor_aberto,
                   p.tipo_notinha
            FROM pagamento_fiado p
            JOIN cliente c ON c.id = p.cliente_id
            WHERE p.tipo = 'DEBITO_INICIAL'
            UNION ALL
            SELECT 'V' || pf.id, c.id, c.nome,
                   v.id, NULL AS documento,
                   'Parcela ' || pf.numero || '/' || cnt.total || COALESCE(' · ' || v.tipo_notinha, ''),
                   pf.vencimento, pf.valor, pf.valor_aberto,
                   v.tipo_notinha
            FROM parcela_fiado pf
            JOIN venda v ON v.id = pf.venda_id
            JOIN cliente c ON c.id = v.cliente_id
            JOIN LATERAL (SELECT COUNT(*) AS total FROM parcela_fiado x WHERE x.venda_id = v.id) cnt ON true
            WHERE v.cancelada_em IS NULL
            """;

    @Transactional(readOnly = true)
    public Pagina listar(String q, String status, LocalDate de, LocalDate ate, String tipo, int pagina) {
        int porPagina = 50;
        var params = new MapSqlParameterSource()
                .addValue("q", q == null ? "" : q.trim())
                .addValue("de", de)
                .addValue("ate", ate)
                .addValue("tipo", tipo == null ? "" : tipo.trim())
                .addValue("limite", porPagina)
                .addValue("offset", Math.max(0, pagina - 1) * porPagina);

        String filtro = """
                WHERE (:q = '' OR unaccent(t.cliente_nome) ILIKE unaccent('%' || :q || '%')
                       OR CAST(t.notinha AS text) = :q
                       OR t.documento = :q OR t.documento LIKE :q || '/%')
                  AND (CAST(:de AS date) IS NULL OR t.vencimento >= :de)
                  AND (CAST(:ate AS date) IS NULL OR t.vencimento <= :ate)
                  AND (:tipo = '' OR t.tipo_notinha = :tipo)
                """ + condicaoStatus(status);

        // numa busca por nome/notinha, as parcelas em aberto vêm primeiro (são as
        // acionáveis); o histórico já quitado fica embaixo. Sem busca, mantém a
        // ordem cronológica por vencimento.
        boolean temBusca = q != null && !q.trim().isEmpty();
        String ordem = temBusca
                ? " ORDER BY (t.valor_aberto > 0) DESC, t.vencimento, t.id "
                // sem busca: cronológico, mas datas corrompidas do legado (ano < 2000)
                // vão para o fim em vez de poluir o topo da tela
                : " ORDER BY (t.vencimento < DATE '2000-01-01') ASC, t.vencimento, t.id ";

        List<Conta> contas = jdbc.query(
                "SELECT * FROM (" + FONTE + ") t " + filtro
                        + ordem + "LIMIT :limite OFFSET :offset",
                params,
                (rs, i) -> {
                    BigDecimal valor = rs.getBigDecimal("valor");
                    BigDecimal aberto = rs.getBigDecimal("valor_aberto");
                    return new Conta(rs.getString("id"), rs.getLong("cliente_id"),
                            rs.getString("cliente_nome"),
                            rs.getObject("notinha") == null ? null : rs.getLong("notinha"),
                            rs.getString("documento"),
                            rs.getString("descricao"), rs.getDate("vencimento").toLocalDate(),
                            valor, aberto, statusDe(valor, aberto, rs.getDate("vencimento").toLocalDate()));
                });

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + FONTE + ") t " + filtro, params, Long.class);

        return new Pagina(contas, total == null ? 0 : total, Math.max(1, pagina), porPagina, totais());
    }

    /**
     * Conferência da gaveta: as notinhas EM ABERTO agrupadas (venda = mesmo
     * venda_id; carnê SET = mesmo prefixo do documento antes da "/"), em ordem
     * alfabética por cliente. Cada linha carrega os refs ("V123"/"L45") das
     * parcelas que a compõem, para a baixa/ajuste agir só nelas.
     */
    @Transactional(readOnly = true)
    public PaginaGaveta gaveta(String q, int pagina) {
        // conferência é uma passada única A→Z: joga tudo numa página só (hoje ~4 mil
        // notinhas), sem obrigar a paginar no meio da conferência com a gaveta.
        int porPagina = 20000;
        var params = new MapSqlParameterSource()
                .addValue("q", q == null ? "" : q.trim())
                .addValue("limite", porPagina)
                .addValue("offset", Math.max(0, pagina - 1) * porPagina);

        // linhas em aberto, já com a chave da notinha e um rótulo legível
        String linhas = "SELECT t.*, left(t.id, 1) AS origem, "
                + "CASE WHEN left(t.id, 1) = 'V' THEN 'V' || t.notinha "
                + "     ELSE 'L:' || t.cliente_id || ':' "
                + "          || COALESCE(NULLIF(split_part(t.documento, '/', 1), ''), t.id) END AS notinha_key, "
                + "CASE WHEN left(t.id, 1) = 'V' THEN 'Nº ' || t.notinha "
                + "     ELSE COALESCE(NULLIF(split_part(t.documento, '/', 1), ''), 's/nº') END AS rotulo "
                + "FROM (" + FONTE + ") t WHERE t.valor_aberto > 0";

        String agrupado = "SELECT notinha_key, MIN(origem) AS origem, cliente_id, "
                + "MIN(cliente_nome) AS cliente_nome, MIN(rotulo) AS rotulo, MIN(tipo_notinha) AS tipo, "
                + "MIN(vencimento) AS vencimento, SUM(valor_aberto) AS total_aberto, "
                + "array_agg(id ORDER BY vencimento, id) AS refs "
                + "FROM (" + linhas + ") g "
                + "WHERE (:q = '' OR unaccent(cliente_nome) ILIKE unaccent('%' || :q || '%') "
                + "       OR rotulo ILIKE '%' || :q || '%') "
                + "GROUP BY notinha_key, cliente_id";

        List<NotinhaAberta> notinhas = jdbc.query(
                agrupado + " ORDER BY unaccent(lower(MIN(cliente_nome))), MIN(vencimento), notinha_key "
                        + "LIMIT :limite OFFSET :offset",
                params,
                (rs, i) -> new NotinhaAberta(
                        rs.getString("notinha_key"), rs.getString("origem"),
                        rs.getLong("cliente_id"), rs.getString("cliente_nome"),
                        rs.getString("rotulo"), rs.getString("tipo"),
                        rs.getDate("vencimento").toLocalDate(),
                        rs.getBigDecimal("total_aberto"),
                        List.of((String[]) rs.getArray("refs").getArray())));

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + agrupado + ") z", params, Long.class);
        return new PaginaGaveta(notinhas, total == null ? 0 : total, Math.max(1, pagina), porPagina);
    }

    /** KPIs gerais (sem filtro): retrato do crediário inteiro. */
    private Totais totais() {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(t.valor_aberto), 0) AS total_aberto,
                       COALESCE(SUM(t.valor_aberto) FILTER (
                           WHERE t.vencimento < CAST(now() AT TIME ZONE 'America/Sao_Paulo' AS date)), 0) AS total_vencido,
                       COUNT(*) FILTER (WHERE t.valor_aberto > 0) AS parcelas_abertas,
                       (SELECT COALESCE(SUM(p.valor), 0) FROM pagamento_fiado p
                        LEFT JOIN venda vx ON vx.id = p.venda_id
                        WHERE p.tipo NOT IN ('DEBITO_INICIAL', 'BAIXA') AND p.valor > 0
                          AND vx.cancelada_em IS NULL
                          AND date_trunc('month', p.data AT TIME ZONE 'America/Sao_Paulo')
                              = date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo')) AS recebido_mes
                FROM (""" + FONTE + ") t WHERE t.valor_aberto > 0",
                new MapSqlParameterSource(),
                (rs, i) -> new Totais(rs.getBigDecimal("total_aberto"), rs.getBigDecimal("total_vencido"),
                        rs.getLong("parcelas_abertas"), rs.getBigDecimal("recebido_mes")));
    }

    private String condicaoStatus(String status) {
        return switch (status == null ? "" : status) {
            case "ABERTA" -> " AND t.valor_aberto > 0";
            case "ATRASADA" -> " AND t.valor_aberto > 0"
                    + " AND t.vencimento < CAST(now() AT TIME ZONE 'America/Sao_Paulo' AS date)";
            case "PARCIAL" -> " AND t.valor_aberto > 0 AND t.valor_aberto < t.valor";
            case "QUITADA" -> " AND t.valor_aberto = 0";
            default -> "";
        };
    }

    private String statusDe(BigDecimal valor, BigDecimal aberto, LocalDate vencimento) {
        if (aberto.signum() == 0) return "QUITADA";
        // mesma zona do SQL: o status exibido nunca discorda do filtro da tela
        if (vencimento.isBefore(LocalDate.now(br.com.loja.pdv.Fuso.LOJA))) return "ATRASADA";
        if (aberto.compareTo(valor) < 0) return "PARCIAL";
        return "ABERTA";
    }
}
