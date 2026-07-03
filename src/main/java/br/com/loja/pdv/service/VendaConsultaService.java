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

    /** Estorno feito no dia de uma venda de dia ANTERIOR: dinheiro que saiu da gaveta hoje. */
    public record SaidaEstorno(Long vendaId, LocalDate diaVenda, String formaPagamento, BigDecimal total) {}

    /** Uma venda do dia, com quem comprou (null = à vista sem cliente). */
    public record VendaDia(Long id, String cliente, String formaPagamento, BigDecimal total) {}

    /** Sangria: dinheiro retirado da gaveta — saída na conferência. */
    public record Retirada(Long id, Instant data, BigDecimal valor, String motivo, String operador) {}

    public record RetiradaRequest(
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Positive(message = "Valor da retirada deve ser positivo")
            @jakarta.validation.constraints.Digits(integer = 8, fraction = 2,
                    message = "Valor da retirada com mais de 2 casas decimais")
            BigDecimal valor,
            String motivo,
            String operador) {}

    /** Um recebimento/entrada do dia, com quem pagou. */
    public record RecebimentoDia(String cliente, String tipo, BigDecimal valor, Long vendaEntrada) {}

    public record Fechamento(BigDecimal saldoAnterior, BigDecimal contagem, BigDecimal esperado,
                             BigDecimal diferenca, String operador, Instant fechadoEm) {}

    public record FecharCaixaRequest(LocalDate data, BigDecimal saldoAnterior,
                                     BigDecimal contagem, String operador) {}

    public record CaixaDia(LocalDate dia, List<Linha> vendasPorForma, List<Linha> recebimentosPorTipo,
                           List<VendaDia> vendasDia, List<RecebimentoDia> recebimentosDia,
                           List<Retirada> retiradasDia, BigDecimal totalRetiradas,
                           List<Linha> estornosPorForma, List<SaidaEstorno> saidasCrossDay,
                           BigDecimal totalVendas, BigDecimal vendidoFiado,
                           BigDecimal totalRecebimentos, BigDecimal totalEstornos,
                           BigDecimal entrouNoCaixa,
                           BigDecimal entradasDinheiro, BigDecimal saidasDinheiro,
                           BigDecimal saldoAnteriorSugerido, Fechamento fechamento) {}

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

        // as mesmas vendas/recebimentos, linha a linha, com QUEM comprou/pagou
        List<VendaDia> vendasDia = jdbc.query("""
                SELECT v.id, c.nome AS cliente, v.forma_pagamento, v.total
                FROM venda v
                LEFT JOIN cliente c ON c.id = v.cliente_id
                WHERE v.cancelada_em IS NULL
                  AND CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                ORDER BY v.id
                """, params,
                (rs, i) -> new VendaDia(rs.getLong("id"), rs.getString("cliente"),
                        rs.getString("forma_pagamento"), rs.getBigDecimal("total")));

        List<RecebimentoDia> recebimentosDia = jdbc.query("""
                SELECT c.nome AS cliente, p.tipo, p.valor, p.venda_id
                FROM pagamento_fiado p
                JOIN cliente c ON c.id = p.cliente_id
                LEFT JOIN venda vx ON vx.id = p.venda_id
                WHERE p.valor > 0 AND p.documento IS NULL AND p.tipo <> 'BAIXA'
                  AND vx.cancelada_em IS NULL
                  AND CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                ORDER BY p.id
                """, params,
                (rs, i) -> new RecebimentoDia(rs.getString("cliente"), rs.getString("tipo"),
                        rs.getBigDecimal("valor"),
                        rs.getObject("venda_id") == null ? null : rs.getLong("venda_id")));

        // com a venda cancelada mantendo a data original (nunca é deletada), dá
        // para separar: estorno de venda de HOJE já se anulou nas somas acima;
        // estorno de venda de dia ANTERIOR é devolução que saiu da gaveta HOJE
        List<SaidaEstorno> saidasCrossDay = jdbc.query("""
                SELECT e.venda_id, e.total, e.forma_pagamento,
                       CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) AS dia_venda
                FROM estorno e JOIN venda v ON v.id = e.venda_id
                WHERE CAST(e.data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                  AND CAST(v.data AT TIME ZONE 'America/Sao_Paulo' AS date) < :dia
                ORDER BY e.venda_id
                """, params,
                (rs, i) -> new SaidaEstorno(rs.getLong("venda_id"),
                        rs.getDate("dia_venda").toLocalDate(),
                        rs.getString("forma_pagamento"), rs.getBigDecimal("total")));

        BigDecimal totalVendas = vendas.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vendidoFiado = vendas.stream().filter(l -> "FIADO".equals(l.rotulo()))
                .map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalReceb = recebimentos.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEstornos = estornos.stream().map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        // à vista + tudo que entrou pelo carnê (entrada de fiado e recebimentos)
        BigDecimal entrou = totalVendas.subtract(vendidoFiado).add(totalReceb);

        // conferência da gaveta: só o que é DINHEIRO físico
        BigDecimal entradasDinheiro = vendas.stream().filter(l -> "DINHEIRO".equals(l.rotulo()))
                .map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(recebimentos.stream().filter(l -> "DINHEIRO".equals(l.rotulo()))
                        .map(Linha::total).reduce(BigDecimal.ZERO, BigDecimal::add));
        // sangrias do dia: saída em dinheiro — sem isso, retirada vira "falta" na conferência
        List<Retirada> retiradasDia = jdbc.query("""
                SELECT id, data, valor, motivo, operador FROM retirada_caixa
                WHERE CAST(data AT TIME ZONE 'America/Sao_Paulo' AS date) = :dia
                ORDER BY id
                """, params,
                (rs, i) -> new Retirada(rs.getLong("id"), rs.getTimestamp("data").toInstant(),
                        rs.getBigDecimal("valor"), rs.getString("motivo"), rs.getString("operador")));
        BigDecimal totalRetiradas = retiradasDia.stream()
                .map(Retirada::valor).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal saidasDinheiro = saidasCrossDay.stream()
                .filter(s -> "DINHEIRO".equals(s.formaPagamento()))
                .map(SaidaEstorno::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(totalRetiradas);

        BigDecimal sugerido = jdbc.query(
                "SELECT contagem FROM fechamento_caixa WHERE data < :dia ORDER BY data DESC LIMIT 1",
                params, rs -> rs.next() ? rs.getBigDecimal(1) : null);
        Fechamento fechamento = jdbc.query(
                "SELECT saldo_anterior, contagem, esperado, diferenca, operador, fechado_em "
                        + "FROM fechamento_caixa WHERE data = :dia",
                params, rs -> rs.next()
                        ? new Fechamento(rs.getBigDecimal("saldo_anterior"), rs.getBigDecimal("contagem"),
                                rs.getBigDecimal("esperado"), rs.getBigDecimal("diferenca"),
                                rs.getString("operador"), rs.getTimestamp("fechado_em").toInstant())
                        : null);

        return new CaixaDia(dia, vendas, recebimentos, vendasDia, recebimentosDia,
                retiradasDia, totalRetiradas,
                estornos, saidasCrossDay,
                totalVendas, vendidoFiado, totalReceb, totalEstornos, entrou,
                entradasDinheiro, saidasDinheiro, sugerido, fechamento);
    }

    /** Registra uma sangria (agora, com o timestamp do momento). */
    @Transactional
    public Retirada registrarRetirada(RetiradaRequest req) {
        Long id = jdbc.queryForObject("""
                INSERT INTO retirada_caixa (valor, motivo, operador)
                VALUES (:valor, :motivo, :operador) RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("valor", req.valor())
                        .addValue("motivo", req.motivo() == null || req.motivo().isBlank()
                                ? null : req.motivo().trim())
                        .addValue("operador", req.operador()),
                Long.class);
        return new Retirada(id, Instant.now(), req.valor(), req.motivo(), req.operador());
    }

    /**
     * Grava (ou regrava) o fechamento do dia. A operadora digita saldo anterior
     * e contagem; esperado e diferença são recalculados AQUI do movimento real —
     * o que fica gravado nunca depende de conta feita na tela.
     */
    @Transactional
    public Fechamento fecharCaixa(FecharCaixaRequest req) {
        LocalDate dia = req.data() != null ? req.data() : LocalDate.now(br.com.loja.pdv.Fuso.LOJA);
        if (dia.isAfter(LocalDate.now(br.com.loja.pdv.Fuso.LOJA))) {
            throw new RegraNegocioException("Não dá para fechar o caixa de um dia futuro");
        }
        if (req.contagem() == null) {
            throw new RegraNegocioException("Informe a contagem do caixa (quanto tem na gaveta)");
        }
        BigDecimal saldoAnterior = req.saldoAnterior() != null ? req.saldoAnterior() : BigDecimal.ZERO;

        CaixaDia mov = caixaDia(dia);
        BigDecimal esperado = saldoAnterior.add(mov.entradasDinheiro()).subtract(mov.saidasDinheiro());
        BigDecimal diferenca = req.contagem().subtract(esperado);

        jdbc.update("""
                INSERT INTO fechamento_caixa (data, saldo_anterior, contagem, esperado, diferenca, operador)
                VALUES (:data, :saldoAnterior, :contagem, :esperado, :diferenca, :operador)
                ON CONFLICT (data) DO UPDATE SET
                    saldo_anterior = EXCLUDED.saldo_anterior, contagem = EXCLUDED.contagem,
                    esperado = EXCLUDED.esperado, diferenca = EXCLUDED.diferenca,
                    operador = EXCLUDED.operador, fechado_em = now()
                """,
                new MapSqlParameterSource()
                        .addValue("data", dia).addValue("saldoAnterior", saldoAnterior)
                        .addValue("contagem", req.contagem()).addValue("esperado", esperado)
                        .addValue("diferenca", diferenca).addValue("operador", req.operador()));

        return new Fechamento(saldoAnterior, req.contagem(), esperado, diferenca,
                req.operador(), Instant.now());
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
