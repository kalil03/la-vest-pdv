package br.com.loja.pdv.service;

import br.com.loja.pdv.Fuso;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Relatório gerencial do PDV: um único cálculo devolve tudo que a tela de
 * Relatórios mostra. Duas naturezas de número convivem aqui e NÃO se misturam:
 *
 *  • MOVIMENTO do período [de, ate] — vendas, recebimentos, rankings. Depende
 *    do filtro de datas.
 *  • RETRATO do crediário AGORA — total em aberto, vencido, aging, devedores.
 *    Independe do período (é a foto do que ainda falta receber hoje).
 *
 * Venda cancelada (cancelada_em) fica fora de toda soma financeira. Recebido =
 * lançamentos do sistema (documento IS NULL), sem o histórico migrado do SET e
 * sem estornos (BAIXA/DEBITO_INICIAL) — mesma regra do caixa e do fechamento.
 */
@Service
public class RelatorioService {

    private final NamedParameterJdbcTemplate jdbc;

    public RelatorioService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- movimento do período ----
    public record Kpis(BigDecimal faturamento, long qtdVendas, BigDecimal ticketMedio,
                       long itensVendidos, BigDecimal descontoTotal, BigDecimal recebido,
                       BigDecimal vendidoAVista, BigDecimal vendidoFiado) {}

    // ---- retrato do crediário agora ----
    public record Crediario(BigDecimal totalAberto, BigDecimal totalVencido,
                            long parcelasAbertas, long clientesDevedores,
                            BigDecimal aVencer, BigDecimal ate30, BigDecimal de31a60,
                            BigDecimal de61a90, BigDecimal mais90) {}

    public record Linha(String rotulo, long qtd, BigDecimal total) {}
    public record ProdutoLinha(String codigo, String nome, long qtd, BigDecimal total) {}
    public record VendedorLinha(String vendedor, long qtd, BigDecimal aVista,
                                BigDecimal aPrazo, BigDecimal total) {}
    public record ClienteLinha(Long clienteId, String cliente, long qtd, BigDecimal total) {}
    public record DevedorLinha(Long clienteId, String cliente, long notas,
                               BigDecimal aberto, BigDecimal vencido) {}

    public record Relatorio(LocalDate de, LocalDate ate, Kpis kpis, Crediario crediario,
                            List<Linha> porForma, List<Linha> porDia,
                            List<ProdutoLinha> topProdutos, List<VendedorLinha> porVendedor,
                            List<ClienteLinha> topClientes, List<DevedorLinha> maioresDevedores) {}

    /** Parcelas AINDA em aberto, das duas origens (carnê SET migrado + vendas fiado). */
    private static final String PARC_ABERTO = """
            SELECT c.id AS cliente_id, c.nome AS cliente_nome,
                   CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS vencimento,
                   COALESCE(p.valor_aberto, 0) AS aberto
            FROM pagamento_fiado p JOIN cliente c ON c.id = p.cliente_id
            WHERE p.tipo = 'DEBITO_INICIAL' AND COALESCE(p.valor_aberto, 0) > 0
            UNION ALL
            SELECT c.id, c.nome, pf.vencimento, pf.valor_aberto
            FROM parcela_fiado pf
            JOIN venda v ON v.id = pf.venda_id
            JOIN cliente c ON c.id = v.cliente_id
            WHERE v.cancelada_em IS NULL AND pf.valor_aberto > 0
            """;

    @Transactional(readOnly = true)
    public Relatorio gerar(LocalDate de, LocalDate ate) {
        LocalDate hoje = LocalDate.now(Fuso.LOJA);
        if (de == null) de = hoje.withDayOfMonth(1);
        if (ate == null) ate = hoje;
        if (ate.isBefore(de)) { LocalDate t = de; de = ate; ate = t; }

        var p = new MapSqlParameterSource()
                .addValue("de", de)
                .addValue("ate", ate)
                .addValue("hoje", hoje)
                .addValue("d30", hoje.minusDays(30))
                .addValue("d60", hoje.minusDays(60))
                .addValue("d90", hoje.minusDays(90));

        return new Relatorio(de, ate, kpis(p), crediario(p),
                porForma(p), porDia(p), topProdutos(p), porVendedor(p),
                topClientes(p), maioresDevedores(p));
    }

    // filtro-padrão do período sobre a venda
    private static final String NO_PERIODO =
            " v.cancelada_em IS NULL AND CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) BETWEEN :de AND :ate ";

    private Kpis kpis(MapSqlParameterSource p) {
        Kpis vendas = jdbc.queryForObject("""
                SELECT COUNT(*) AS qtd,
                       COALESCE(SUM(v.total), 0) AS faturamento,
                       COALESCE(SUM(v.desconto), 0) AS desconto,
                       COALESCE(SUM(v.total) FILTER (WHERE v.forma_pagamento = 'FIADO'), 0) AS fiado,
                       COALESCE(SUM(v.total) FILTER (WHERE v.forma_pagamento <> 'FIADO'), 0) AS avista
                FROM venda v WHERE""" + NO_PERIODO, p,
                (rs, i) -> {
                    long qtd = rs.getLong("qtd");
                    BigDecimal fat = rs.getBigDecimal("faturamento");
                    BigDecimal ticket = qtd == 0 ? BigDecimal.ZERO
                            : fat.divide(BigDecimal.valueOf(qtd), 2, RoundingMode.HALF_UP);
                    return new Kpis(fat, qtd, ticket, 0L, rs.getBigDecimal("desconto"),
                            BigDecimal.ZERO, rs.getBigDecimal("avista"), rs.getBigDecimal("fiado"));
                });

        Long itens = jdbc.queryForObject("""
                SELECT COALESCE(SUM(i.quantidade), 0)
                FROM item_venda i JOIN venda v ON v.id = i.venda_id WHERE""" + NO_PERIODO, p, Long.class);

        // recebido = recebimentos de carnê + entradas de fiado do período (lançamentos
        // do sistema, sem o histórico do SET e sem estornos) — mesma base do caixa
        BigDecimal recebido = jdbc.queryForObject("""
                SELECT COALESCE(SUM(pf.valor), 0)
                FROM pagamento_fiado pf
                LEFT JOIN venda vx ON vx.id = pf.venda_id
                WHERE pf.valor > 0 AND pf.documento IS NULL
                  AND pf.tipo NOT IN ('DEBITO_INICIAL', 'BAIXA')
                  AND vx.cancelada_em IS NULL
                  AND CAST(pf.data AT TIME ZONE 'America/Sao_Paulo' AS date) BETWEEN :de AND :ate
                """, p, BigDecimal.class);

        return new Kpis(vendas.faturamento(), vendas.qtdVendas(), vendas.ticketMedio(),
                itens == null ? 0 : itens, vendas.descontoTotal(), recebido,
                vendas.vendidoAVista(), vendas.vendidoFiado());
    }

    private Crediario crediario(MapSqlParameterSource p) {
        return jdbc.queryForObject("SELECT "
                + "COALESCE(SUM(aberto), 0) AS total_aberto, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento < :hoje), 0) AS vencido, "
                + "COUNT(*) AS parcelas, "
                + "COUNT(DISTINCT cliente_id) AS clientes, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento >= :hoje), 0) AS a_vencer, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento < :hoje AND vencimento >= :d30), 0) AS ate30, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento < :d30 AND vencimento >= :d60), 0) AS de31a60, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento < :d60 AND vencimento >= :d90), 0) AS de61a90, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento < :d90), 0) AS mais90 "
                + "FROM (" + PARC_ABERTO + ") q", p,
                (rs, i) -> new Crediario(rs.getBigDecimal("total_aberto"), rs.getBigDecimal("vencido"),
                        rs.getLong("parcelas"), rs.getLong("clientes"),
                        rs.getBigDecimal("a_vencer"), rs.getBigDecimal("ate30"),
                        rs.getBigDecimal("de31a60"), rs.getBigDecimal("de61a90"),
                        rs.getBigDecimal("mais90")));
    }

    /** Vendas por forma de pagamento; venda MISTA é expandida pelas suas formas. */
    private List<Linha> porForma(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT rotulo, COUNT(*) AS qtd, COALESCE(SUM(valor), 0) AS total FROM (
                    SELECT v.forma_pagamento AS rotulo, v.total AS valor
                    FROM venda v WHERE""" + NO_PERIODO + """
                      AND v.forma_pagamento <> 'MISTO'
                    UNION ALL
                    SELECT pv.forma, pv.valor
                    FROM venda v JOIN pagamento_venda pv ON pv.venda_id = v.id
                    WHERE""" + NO_PERIODO + """
                      AND v.forma_pagamento = 'MISTO'
                ) t GROUP BY rotulo ORDER BY total DESC
                """, p,
                (rs, i) -> new Linha(rs.getString("rotulo"), rs.getLong("qtd"), rs.getBigDecimal("total")));
    }

    private List<Linha> porDia(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS dia,
                       COUNT(*) AS qtd, COALESCE(SUM(v.total), 0) AS total
                FROM venda v WHERE""" + NO_PERIODO + " GROUP BY 1 ORDER BY 1", p,
                (rs, i) -> new Linha(rs.getDate("dia").toString(), rs.getLong("qtd"), rs.getBigDecimal("total")));
    }

    private List<ProdutoLinha> topProdutos(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT pr.codigo, pr.nome, SUM(i.quantidade) AS qtd,
                       COALESCE(SUM(i.preco_unit * i.quantidade), 0) AS total
                FROM item_venda i
                JOIN venda v ON v.id = i.venda_id
                JOIN variacao va ON va.id = i.variacao_id
                JOIN produto pr ON pr.id = va.produto_id
                WHERE""" + NO_PERIODO + """
                GROUP BY pr.id, pr.codigo, pr.nome
                ORDER BY qtd DESC, total DESC LIMIT 12
                """, p,
                (rs, i) -> new ProdutoLinha(rs.getString("codigo"), rs.getString("nome"),
                        rs.getLong("qtd"), rs.getBigDecimal("total")));
    }

    private List<VendedorLinha> porVendedor(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT COALESCE(vd.nome, 'Sem vendedor') AS vendedor, COUNT(*) AS qtd,
                       COALESCE(SUM(v.total) FILTER (WHERE v.forma_pagamento <> 'FIADO'), 0) AS a_vista,
                       COALESCE(SUM(v.total) FILTER (WHERE v.forma_pagamento = 'FIADO'), 0) AS a_prazo,
                       COALESCE(SUM(v.total), 0) AS total
                FROM venda v
                LEFT JOIN vendedor vd ON vd.id = v.vendedor_id
                WHERE""" + NO_PERIODO + " GROUP BY 1 ORDER BY total DESC, vendedor", p,
                (rs, i) -> new VendedorLinha(rs.getString("vendedor"), rs.getLong("qtd"),
                        rs.getBigDecimal("a_vista"), rs.getBigDecimal("a_prazo"),
                        rs.getBigDecimal("total")));
    }

    private List<ClienteLinha> topClientes(MapSqlParameterSource p) {
        return jdbc.query("""
                SELECT c.id, c.nome, COUNT(*) AS qtd, COALESCE(SUM(v.total), 0) AS total
                FROM venda v JOIN cliente c ON c.id = v.cliente_id
                WHERE""" + NO_PERIODO + """
                GROUP BY c.id, c.nome ORDER BY total DESC LIMIT 10
                """, p,
                (rs, i) -> new ClienteLinha(rs.getLong("id"), rs.getString("nome"),
                        rs.getLong("qtd"), rs.getBigDecimal("total")));
    }

    private List<DevedorLinha> maioresDevedores(MapSqlParameterSource p) {
        return jdbc.query("SELECT cliente_id, MIN(cliente_nome) AS nome, COUNT(*) AS notas, "
                + "COALESCE(SUM(aberto), 0) AS aberto, "
                + "COALESCE(SUM(aberto) FILTER (WHERE vencimento < :hoje), 0) AS vencido "
                + "FROM (" + PARC_ABERTO + ") q GROUP BY cliente_id ORDER BY aberto DESC LIMIT 10", p,
                (rs, i) -> new DevedorLinha(rs.getLong("cliente_id"), rs.getString("nome"),
                        rs.getLong("notas"), rs.getBigDecimal("aberto"), rs.getBigDecimal("vencido")));
    }
}
