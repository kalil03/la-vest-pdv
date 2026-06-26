package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Condicional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CondicionalRepository extends JpaRepository<Condicional, Long> {

    List<Condicional> findByStatusOrderByDataSaidaDesc(String status);

    List<Condicional> findAllByOrderByDataSaidaDesc();
}
