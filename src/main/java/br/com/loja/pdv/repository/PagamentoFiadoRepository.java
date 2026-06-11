package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.PagamentoFiado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoFiadoRepository extends JpaRepository<PagamentoFiado, Long> {
}
