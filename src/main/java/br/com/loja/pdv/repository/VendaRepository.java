package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Venda;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendaRepository extends JpaRepository<Venda, Long> {
}
