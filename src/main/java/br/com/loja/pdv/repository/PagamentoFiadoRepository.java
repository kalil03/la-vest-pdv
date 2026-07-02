package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.PagamentoFiado;
import br.com.loja.pdv.domain.TipoPagamentoFiado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PagamentoFiadoRepository extends JpaRepository<PagamentoFiado, Long> {

    List<PagamentoFiado> findByVendaId(Long vendaId);

    /** Histórico de recebimentos reais (exclui dívida migrada). */
    List<PagamentoFiado> findTop3ByClienteIdAndTipoNotOrderByDataDesc(
            Long clienteId, TipoPagamentoFiado tipoExcluido);

    /** Parcelas migradas do SET no razão do cliente, mais antigas primeiro. */
    List<PagamentoFiado> findByClienteIdAndTipoOrderByDataAsc(Long clienteId, TipoPagamentoFiado tipo);

    /**
     * Abate atômico da parcela migrada do SET (DEBITO_INICIAL) — mesmo padrão
     * do baixarEstoque: o WHERE revalida o restante no banco; 0 linhas = outra
     * operação passou na frente, o serviço aborta e o rollback desfaz tudo.
     * valorAberto NULL nunca casa com >= (parcela histórica já paga).
     */
    @Modifying
    @Query("""
            UPDATE PagamentoFiado p SET p.valorAberto = p.valorAberto - :valor
            WHERE p.id = :id AND p.valorAberto >= :valor
            """)
    int abaterDebito(@Param("id") Long id, @Param("valor") BigDecimal valor);
}
