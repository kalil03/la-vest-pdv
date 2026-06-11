package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Vendedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendedorRepository extends JpaRepository<Vendedor, Long> {
    List<Vendedor> findByAtivoTrueOrderByNome();
}
