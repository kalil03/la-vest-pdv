package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.PagamentoFiado;
import br.com.loja.pdv.domain.TipoPagamentoFiado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagamentoFiadoRepository extends JpaRepository<PagamentoFiado, Long> {

    List<PagamentoFiado> findByVendaId(Long vendaId);

    /** Histórico de recebimentos reais (exclui dívida migrada). */
    List<PagamentoFiado> findTop3ByClienteIdAndTipoNotOrderByDataDesc(
            Long clienteId, TipoPagamentoFiado tipoExcluido);

    /** Parcelas migradas do SET no razão do cliente, mais antigas primeiro. */
    List<PagamentoFiado> findByClienteIdAndTipoOrderByDataAsc(Long clienteId, TipoPagamentoFiado tipo);
}
