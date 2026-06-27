package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /** Busca por nome (sem acento), CPF ou telefone — um campo só na tela. */
    @Query(value = """
            SELECT c.* FROM cliente c
            WHERE unaccent(c.nome) ILIKE unaccent('%' || :q || '%')
               OR c.cpf LIKE :q || '%'
               OR c.telefone LIKE '%' || :q || '%'
            ORDER BY c.nome
            LIMIT 200
            """, nativeQuery = true)
    List<Cliente> buscar(@Param("q") String q);

    boolean existsByCpf(String cpf);

    /** Busca por nº da notinha (venda): quem é o dono daquela venda. */
    @Query(value = """
            SELECT c.* FROM cliente c JOIN venda v ON v.cliente_id = c.id
            WHERE v.id = :vendaId
            """, nativeQuery = true)
    java.util.Optional<Cliente> donoDaVenda(@Param("vendaId") Long vendaId);

    /**
     * Regra de ouro nº 1: a dívida nunca é armazenada, é sempre calculada.
     * Saldo devedor = soma das vendas FIADO - soma dos pagamentos do carnê.
     */
    @Query(value = """
            SELECT COALESCE((SELECT SUM(v.total) FROM venda v
                             WHERE v.cliente_id = :id AND v.forma_pagamento = 'FIADO'), 0)
                 - COALESCE((SELECT SUM(p.valor) FROM pagamento_fiado p
                             WHERE p.cliente_id = :id), 0)
            """, nativeQuery = true)
    BigDecimal saldoDevedor(@Param("id") Long id);

    /** Saldos de vários clientes de uma vez (listagem sem N+1). */
    @Query(value = """
            SELECT c.id AS "clienteId",
                   COALESCE(v.tot, 0) - COALESCE(p.tot, 0) AS "saldo"
            FROM cliente c
            LEFT JOIN (SELECT cliente_id, SUM(total) AS tot FROM venda
                       WHERE forma_pagamento = 'FIADO' GROUP BY cliente_id) v ON v.cliente_id = c.id
            LEFT JOIN (SELECT cliente_id, SUM(valor) AS tot FROM pagamento_fiado
                       GROUP BY cliente_id) p ON p.cliente_id = c.id
            WHERE c.id IN (:ids)
            """, nativeQuery = true)
    List<SaldoCliente> saldosPorCliente(@Param("ids") Collection<Long> ids);

    interface SaldoCliente {
        Long getClienteId();
        BigDecimal getSaldo();
    }

    /**
     * "Score da casa": prazo médio, em dias, entre cada pagamento e a venda
     * fiado mais recente antes dele. Heurística com dados internos.
     * NULL se o cliente nunca pagou um fiado.
     */
    @Query(value = """
            SELECT AVG(EXTRACT(EPOCH FROM (p.data - u.data_venda)) / 86400.0)
            FROM pagamento_fiado p
            JOIN LATERAL (
                SELECT MAX(v.data) AS data_venda FROM venda v
                WHERE v.cliente_id = p.cliente_id
                  AND v.forma_pagamento = 'FIADO'
                  AND v.data <= p.data
            ) u ON u.data_venda IS NOT NULL
            WHERE p.cliente_id = :id
              AND p.tipo NOT IN ('DEBITO_INICIAL', 'BAIXA') -- dívida migrada e baixa não são pagamento
              AND p.venda_id IS NULL -- entrada nasce junto da venda: contaria "pagou em 0 dias"
            """, nativeQuery = true)
    Double prazoMedioPagamentoDias(@Param("id") Long id);

    /**
     * Parcelas vencidas em aberto do cliente — alerta de risco no caixa antes de
     * vender mais fiado. Junta as duas origens (carnê migrado do SET + parcelas
     * das vendas), igual à régua de cobrança. Calculado de valor_aberto.
     */
    @Query(value = """
            SELECT COALESCE(SUM(valor_aberto), 0) AS valor, COUNT(*) AS qtd
            FROM (
                SELECT COALESCE(p.valor_aberto, 0) AS valor_aberto
                FROM pagamento_fiado p
                WHERE p.cliente_id = :id AND p.tipo = 'DEBITO_INICIAL'
                  AND COALESCE(p.valor_aberto, 0) > 0
                  AND CAST(p.data AT TIME ZONE 'America/Sao_Paulo' AS date) < CURRENT_DATE
                UNION ALL
                SELECT pf.valor_aberto
                FROM parcela_fiado pf JOIN venda v ON v.id = pf.venda_id
                WHERE v.cliente_id = :id
                  AND COALESCE(pf.valor_aberto, 0) > 0
                  AND pf.vencimento < CURRENT_DATE
            ) t
            """, nativeQuery = true)
    Vencido vencidas(@Param("id") Long id);

    interface Vencido {
        BigDecimal getValor();
        long getQtd();
    }
}
