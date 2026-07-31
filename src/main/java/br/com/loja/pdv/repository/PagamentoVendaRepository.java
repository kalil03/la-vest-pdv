package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.PagamentoVenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagamentoVendaRepository extends JpaRepository<PagamentoVenda, Long> {

    List<PagamentoVenda> findByVendaId(Long vendaId);
}
