package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.ParcelaFiado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParcelaFiadoRepository extends JpaRepository<ParcelaFiado, Long> {

    /** Cronograma de todas as vendas fiado do cliente, mais antigas primeiro. */
    @Query("""
            SELECT p FROM ParcelaFiado p
            JOIN FETCH p.venda v
            WHERE v.cliente.id = :clienteId
            ORDER BY p.vencimento, p.id
            """)
    List<ParcelaFiado> doCliente(@Param("clienteId") Long clienteId);
}
