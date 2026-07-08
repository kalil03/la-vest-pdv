package br.com.loja.pdv.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Consulta das NFC-e emitidas para a tela de gestão de notas: lista com filtro
 * por status (AUTORIZADO / ERRO / PROCESSANDO / CANCELADO) e busca por número da
 * venda, chave ou cliente. Leitura pura (jdbc) — a emissão/reemissão continua no
 * {@link NfceEmissaoService}.
 */
@Service
public class NfceConsultaService {

    private final NamedParameterJdbcTemplate jdbc;

    public NfceConsultaService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NfceLinha(Long id, Long vendaId, String status, String chaveAcesso,
                            String protocolo, String mensagem, Instant criadaEm, Instant autorizadaEm,
                            BigDecimal total, Instant vendaData, String formaPagamento,
                            String clienteNome, boolean vendaCancelada, boolean temXml) {}

    @Transactional(readOnly = true)
    public List<NfceLinha> listar(String status, String q) {
        String qLike = "%" + (q == null ? "" : q.trim()) + "%";
        var params = new MapSqlParameterSource()
                .addValue("status", status == null ? "" : status.trim())
                .addValue("q", q == null ? "" : q.trim())
                .addValue("like", qLike);

        return jdbc.query("""
                SELECT n.id, n.venda_id, n.status, n.chave_acesso, n.protocolo, n.mensagem,
                       n.criada_em, n.autorizada_em, (n.xml IS NOT NULL) AS tem_xml,
                       v.total, v.data AS venda_data, v.forma_pagamento,
                       v.cancelada_em, c.nome AS cliente_nome
                FROM nfce n
                JOIN venda v ON v.id = n.venda_id
                LEFT JOIN cliente c ON c.id = v.cliente_id
                WHERE (:status = '' OR n.status = :status)
                  AND (:q = '' OR n.chave_acesso ILIKE :like
                       OR CAST(v.id AS text) = :q OR c.nome ILIKE :like)
                ORDER BY n.criada_em DESC
                LIMIT 300
                """, params, (rs, i) -> new NfceLinha(
                        rs.getLong("id"), rs.getLong("venda_id"), rs.getString("status"),
                        rs.getString("chave_acesso"), rs.getString("protocolo"), rs.getString("mensagem"),
                        rs.getTimestamp("criada_em").toInstant(),
                        rs.getTimestamp("autorizada_em") == null ? null : rs.getTimestamp("autorizada_em").toInstant(),
                        rs.getBigDecimal("total"),
                        rs.getTimestamp("venda_data") == null ? null : rs.getTimestamp("venda_data").toInstant(),
                        rs.getString("forma_pagamento"), rs.getString("cliente_nome"),
                        rs.getTimestamp("cancelada_em") != null, rs.getBoolean("tem_xml")));
    }
}
