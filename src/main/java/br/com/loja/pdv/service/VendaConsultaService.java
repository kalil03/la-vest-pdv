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
                             String observacao, int itens, boolean temRecebimento) {}

    public record Totais(long quantidade, BigDecimal soma) {}

    public record Pagina(List<VendaLinha> vendas, long total, int pagina, int porPagina, Totais totais) {}

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
                        rs.getInt("itens"), rs.getBoolean("tem_recebimento")));

        Totais totais = jdbc.queryForObject(
                "SELECT COUNT(*) AS qtd, COALESCE(SUM(v.total), 0) AS soma " + base, params,
                (rs, i) -> new Totais(rs.getLong("qtd"), rs.getBigDecimal("soma")));

        return new Pagina(vendas, totais.quantidade(), Math.max(1, pagina), porPagina, totais);
    }
}
