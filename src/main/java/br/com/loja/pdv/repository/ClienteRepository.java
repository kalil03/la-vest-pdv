package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    @Query(value = """
            SELECT c.* FROM cliente c
            WHERE unaccent(c.nome) ILIKE unaccent('%' || :q || '%')
            ORDER BY c.nome
            LIMIT 10
            """, nativeQuery = true)
    List<Cliente> buscar(@Param("q") String q);

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

    /**
     * "Score da casa": prazo médio, em dias, entre cada pagamento e a venda
     * fiado mais recente antes dele. Heurística simples com os dados internos
     * (a consulta Serasa é Fase 3). NULL se o cliente nunca pagou um fiado.
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
            """, nativeQuery = true)
    Double prazoMedioPagamentoDias(@Param("id") Long id);
}
