package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.PagamentoFiado;
import br.com.loja.pdv.domain.TipoPagamentoFiado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PagamentoFiadoRepository extends JpaRepository<PagamentoFiado, Long> {

    List<PagamentoFiado> findByVendaId(Long vendaId);

    /**
     * Crédito "avulso" do cliente: pagamentos de verdade (positivos) que NÃO
     * são entrada de venda. É o montante alocado FIFO nas parcelas abertas
     * para calcular o status de cada uma — entradas ficam de fora porque as
     * parcelas da venda já nascem líquidas de entrada.
     */
    @Query("""
            SELECT COALESCE(SUM(p.valor), 0) FROM PagamentoFiado p
            WHERE p.cliente.id = :clienteId AND p.valor > 0 AND p.venda IS NULL
            """)
    BigDecimal creditoAvulso(@Param("clienteId") Long clienteId);

    /** Histórico de recebimentos reais (exclui dívida migrada). */
    List<PagamentoFiado> findTop3ByClienteIdAndTipoNotOrderByDataDesc(
            Long clienteId, TipoPagamentoFiado tipoExcluido);

    /** Parcelas migradas do SET ainda no razão do cliente, mais antigas primeiro. */
    List<PagamentoFiado> findByClienteIdAndTipoOrderByDataAsc(Long clienteId, TipoPagamentoFiado tipo);
}
