package br.com.loja.pdv.service;

import br.com.loja.pdv.Fuso;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * Fechamento mensal de vendas — espelho da planilha "MOVIMENTO MENSAL".
 * UM único cálculo devolve o JSON; tela, CSV e PDF consomem o MESMO resultado
 * (nunca dois cálculos paralelos). Mês = [dia 1º 00:00, dia 1º seguinte) na
 * zona da loja. Venda cancelada (cancelada_em) fora de TODAS as somas.
 * Eixo sem fonte vira linha rotulada ("Sem tipo", "Sem vendedor") — nunca
 * zero silencioso.
 */
@Service
public class FechamentoMensalService {

    private final NamedParameterJdbcTemplate jdbc;

    public FechamentoMensalService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LinhaCategoria(String categoria, long qtd, BigDecimal total) {}
    public record LinhaVendedor(String vendedor, long qtd, BigDecimal aVista,
                                BigDecimal aPrazo, BigDecimal total) {}
    public record Resumo(long qtd, BigDecimal total) {}
    public record Fechamento(int ano, int mes,
                             List<LinhaCategoria> porCategoria,
                             long qtdVendas, BigDecimal totalGeral,
                             List<LinhaVendedor> porVendedor,
                             Resumo recebimentoMes, Resumo entradasFiado, Resumo retiradas) {}

    /** Ordem fixa das categorias na exibição: Geral, Tênis, Sem tipo. */
    private static int ordemCategoria(String c) {
        return switch (c) { case "Geral" -> 0; case "Tênis" -> 1; default -> 2; };
    }

    @Transactional(readOnly = true)
    public Fechamento gerar(int ano, int mes) {
        if (mes < 1 || mes > 12) {
            throw new RegraNegocioException("Mês inválido: " + mes);
        }
        LocalDate primeiroDia = LocalDate.of(ano, mes, 1);
        var params = new MapSqlParameterSource()
                .addValue("ini", primeiroDia.atStartOfDay(Fuso.LOJA).toInstant().atOffset(ZoneOffset.UTC))
                .addValue("fim", primeiroDia.plusMonths(1).atStartOfDay(Fuso.LOJA).toInstant().atOffset(ZoneOffset.UTC));

        List<LinhaCategoria> categorias = jdbc.query("""
                SELECT COALESCE(v.tipo_notinha, 'Sem tipo') AS categoria,
                       COUNT(*) AS qtd, COALESCE(SUM(v.total), 0) AS total
                FROM venda v
                WHERE v.cancelada_em IS NULL AND v.data >= :ini AND v.data < :fim
                GROUP BY 1
                """, params,
                (rs, i) -> new LinhaCategoria(rs.getString("categoria"),
                        rs.getLong("qtd"), rs.getBigDecimal("total")));
        categorias = categorias.stream()
                .sorted(Comparator.comparingInt(c -> ordemCategoria(c.categoria())))
                .toList();

        long qtdVendas = categorias.stream().mapToLong(LinhaCategoria::qtd).sum();
        BigDecimal totalGeral = categorias.stream()
                .map(LinhaCategoria::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LinhaVendedor> vendedores = jdbc.query("""
                SELECT COALESCE(vd.nome, 'Sem vendedor') AS vendedor, COUNT(*) AS qtd,
                       COALESCE(SUM(v.total) FILTER (WHERE v.forma_pagamento <> 'FIADO'), 0) AS a_vista,
                       COALESCE(SUM(v.total) FILTER (WHERE v.forma_pagamento = 'FIADO'), 0) AS a_prazo,
                       COALESCE(SUM(v.total), 0) AS total
                FROM venda v
                LEFT JOIN vendedor vd ON vd.id = v.vendedor_id
                WHERE v.cancelada_em IS NULL AND v.data >= :ini AND v.data < :fim
                GROUP BY 1 ORDER BY total DESC, vendedor
                """, params,
                (rs, i) -> new LinhaVendedor(rs.getString("vendedor"), rs.getLong("qtd"),
                        rs.getBigDecimal("a_vista"), rs.getBigDecimal("a_prazo"),
                        rs.getBigDecimal("total")));

        // carnê pago no balcão: exclui histórico migrado do SET (documento) e as
        // entradas de fiado (venda_id) — estas ficam em linha própria abaixo
        Resumo recebimento = resumo("""
                SELECT COUNT(*) AS qtd, COALESCE(SUM(p.valor), 0) AS total
                FROM pagamento_fiado p
                WHERE p.valor > 0 AND p.documento IS NULL
                  AND p.tipo NOT IN ('DEBITO_INICIAL', 'BAIXA')
                  AND p.venda_id IS NULL
                  AND p.data >= :ini AND p.data < :fim
                """, params);

        Resumo entradas = resumo("""
                SELECT COUNT(*) AS qtd, COALESCE(SUM(p.valor), 0) AS total
                FROM pagamento_fiado p
                JOIN venda vx ON vx.id = p.venda_id
                WHERE p.valor > 0 AND p.documento IS NULL
                  AND p.tipo NOT IN ('DEBITO_INICIAL', 'BAIXA')
                  AND vx.cancelada_em IS NULL
                  AND p.data >= :ini AND p.data < :fim
                """, params);

        Resumo retiradas = resumo("""
                SELECT COUNT(*) AS qtd, COALESCE(SUM(valor), 0) AS total
                FROM retirada_caixa WHERE data >= :ini AND data < :fim
                """, params);

        return new Fechamento(ano, mes, categorias, qtdVendas, totalGeral,
                vendedores, recebimento, entradas, retiradas);
    }

    private Resumo resumo(String sql, MapSqlParameterSource params) {
        return jdbc.queryForObject(sql, params,
                (rs, i) -> new Resumo(rs.getLong("qtd"), rs.getBigDecimal("total")));
    }
}
