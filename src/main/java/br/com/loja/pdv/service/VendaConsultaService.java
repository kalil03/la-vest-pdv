package br.com.loja.pdv.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Listagem de vendas para conferência/estorno. Venda cancelada some do banco
 * (estorno devolve estoque e desfaz o fiado) — não existe status "cancelada".
 * Vendas fiado com parcela já recebida não podem ser estornadas/editadas.
 */
@Service
public class VendaConsultaService {

    private final NamedParameterJdbcTemplate jdbc;

    public VendaConsultaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record VendaLinha(Long id, Instant data, String clienteNome, String vendedorNome,
                             String formaPagamento, BigDecimal desconto, BigDecimal total,
                             String observacao, int itens, boolean temRecebimento,
                             boolean cancelada) {}

    public record Totais(long quantidade, BigDecimal soma) {}

    public record Pagina(List<VendaLinha> vendas, long total, int pagina, int porPagina, Totais totais) {}

    public record Linha(String rotulo, long qtd, BigDecimal total) {}

    public record CaixaDia(LocalDate dia, List<Linha> vendasPorForma, List<Linha> recebimentosPorTipo,
                           List<Linha> estornosPorForma,
                           BigDecimal totalVendas, BigDecimal vendidoFiado,
                           BigDecimal totalRecebimentos, BigDecimal totalEstornos,
                           BigDecimal entrouNoCaixa) {}

    /**
     * Fechamento do dia: o que foi vendido por forma e o que ENTROU de
     * dinheiro (vendas à vista + entradas e recebimentos de carnê).
     * Venda fiado é venda, mas não é dinheiro no caixa.
     */
    @Transactional(readOnly = true)
    public CaixaDia caixaDia(LocalDate dia) {
        var params = new MapSqlParameterSource().addValue("dia", dia);

        List<Linha> vendas = jdbc.query("""
                SELECT forma_pagamento AS rotulo, COUNT(*) AS qtd, COALESCE(SUM(total), 0) AS total
                FROM venda
                WHERE cancelada_em IS NULL
                  AND CAST(data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                GROUP BY forma_pagamento ORDER BY forma_pagamento
                """, params,
                (rs, i) -> new Linha(rs.getString("rotulo"), rs.getLong("qtd"), rs.getBigDecimal("total")));

        // documento IS NULL = só lançamentos do sistema (histórico migrado do SET
        // fica fora); entrada de fiado de venda cancelada sai junto com a venda
        List<Linha> recebimentos = jdbc.query("""
                SELECT p.tipo AS rotulo, COUNT(*) AS qtd, COALESCE(SUM(p.valor), 0) AS total
                FROM pagamento_fiado p
                LEFT JOIN venda vx ON vx.id = p.venda_id
                WHERE p.valor > 0 AND p.documento IS NULL AND p.tipo <> 'BAIXA'
                  AND vx.cancelada_em IS NULL
                  AND CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                GROUP BY p.tipo ORDER BY p.tipo
                """, params,
                (rs, i) -> new Linha(rs.getString("rotulo"), rs.getLong("qtd"), rs.getBigDecimal("total")));

        // estornos FEITOS neste dia: venda de hoje estornada já some das listas
        // acima (efeito líquido zero na gaveta); venda de dia anterior estornada
        // hoje é dinheiro que saiu da gaveta — a linha existe para a conferência
        // física bater, sem mexer no "entrou no caixa"
        List<Linha> estornos = jdbc.query("""
                SELECT forma_pagamento AS rotulo, COUNT(*) AS qtd, COALESCE(SUM(total), 0) AS total
                FROM estorno
                WHERE CAST(data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                GROUP BY forma_pagamento ORDER BY forma_pagamento
                """, params,
                (rs, i) -> new Linha(rs.getString("rotulo"), rs.getLong("qtd"), rs.getBigDecimal("total")));

        BigDecimal totalVendas = vendas.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vendidoFiado = vendas.stream().filter(l -> "FIADO".equals(l.rotulo()))
                .map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceb = recebimentos.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEstornos = estornos.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        // à vista + tudo que entrou pelo carnê (entrada de fiado e recebimentos)
        BigDecimal entrou = totalVendas.subtract(vendidoFiado).add(totalReceb);

        return new CaixaDia(dia, vendas, recebimentos, estornos,
                totalVendas, vendidoFiado, totalReceb, totalEstornos, entrou);
    }

    @Transactional(readOnly = true)
    public Pagina listar(String q, String forma, LocalDate de, LocalDate ate, int pagina) {
        int porPagina = 50;
        var params = new MapSqlParameterSource()
                .addValue("q", q == null ? "" : q.trim())
                .addValue("forma", forma == null ? "" : forma.trim())
                .addValue("de", de)
                .addValue("ate", ate)
                .addValue("limite", porPagina)
                .addValue("offset", Math.max(0, pagina - 1) * porPagina);

        String filtro = """
                WHERE (:q = '' OR CAST(v.id AS text) = :q
                       OR unaccent(COALESCE(c.nome, '')) ILIKE unaccent('%' || :q || '%'))
                  AND (:forma = '' OR v.forma_pagamento = :forma)
                  AND (CAST(:de AS date) IS NULL
                       OR CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) >= :de)
                  AND (CAST(:ate AS date) IS NULL
                       OR CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) <= :ate)
                """;
        String base = """
                FROM venda v
                LEFT JOIN cliente c ON c.id = v.cliente_id
                LEFT JOIN vendedor vd ON vd.id = v.vendedor_id
                """ + filtro;

        List<VendaLinha> vendas = jdbc.query("""
                SELECT v.id, v.data, v.forma_pagamento, v.desconto, v.total, v.observacao,
                       v.cancelada_em,
                       c.nome AS cliente_nome, vd.nome AS vendedor_nome,
                       (SELECT COUNT(*) FROM item_venda i WHERE i.venda_id = v.id) AS itens,
                       EXISTS (SELECT 1 FROM parcela_fiado p
                               WHERE p.venda_id = v.id AND p.valor_aberto < p.valor) AS tem_recebimento
                """ + base + " ORDER BY v.id DESC LIMIT :limite OFFSET :offset",
                params,
                (rs, i) -> new VendaLinha(rs.getLong("id"), rs.getTimestamp("data").toInstant(),
                        rs.getString("cliente_nome"), rs.getString("vendedor_nome"),
                        rs.getString("forma_pagamento"), rs.getBigDecimal("desconto"),
                        rs.getBigDecimal("total"), rs.getString("observacao"),
                        rs.getInt("itens"), rs.getBoolean("tem_recebimento"),
                        rs.getTimestamp("cancelada_em") != null));

        // a lista mostra as canceladas (com selo), mas a soma financeira não as conta
        Totais totais = jdbc.queryForObject(
                "SELECT COUNT(*) AS qtd, "
                        + "COALESCE(SUM(v.total) FILTER (WHERE v.cancelada_em IS NULL), 0) AS soma " + base,
                params,
                (rs, i) -> new Totais(rs.getLong("qtd"), rs.getBigDecimal("soma")));

        return new Pagina(vendas, totais.quantidade(), Math.max(1, pagina), porPagina, totais);
    }
}
