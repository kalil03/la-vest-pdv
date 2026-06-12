package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Estorno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstornoRepository extends JpaRepository<Estorno, Long> {
    List<Estorno> findTop50ByOrderByDataDesc();
}
