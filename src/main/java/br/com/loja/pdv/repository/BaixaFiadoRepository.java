package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.BaixaFiado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaixaFiadoRepository extends JpaRepository<BaixaFiado, Long> {

    List<BaixaFiado> findByStatusOrderByDataDesc(String status);

    List<BaixaFiado> findAllByOrderByDataDesc();
}
