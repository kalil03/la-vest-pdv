package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.PagamentoFiado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagamentoFiadoRepository extends JpaRepository<PagamentoFiado, Long> {
    List<PagamentoFiado> findByVendaId(Long vendaId);
}
