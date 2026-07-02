package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.ParcelaFiado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /**
     * Abate atômico, no mesmo padrão do baixarEstoque: um único UPDATE cujo
     * WHERE revalida o restante NO BANCO. Se outra operação abateu no meio,
     * retorna 0 e o serviço aborta (rollback desfaz o recebimento inteiro) —
     * sem ler-modificar-gravar em Java, que perderia atualização concorrente.
     */
    @Modifying
    @Query("""
            UPDATE ParcelaFiado p SET p.valorAberto = p.valorAberto - :valor
            WHERE p.id = :id AND p.valorAberto >= :valor
            """)
    int abater(@Param("id") Long id, @Param("valor") BigDecimal valor);
}
