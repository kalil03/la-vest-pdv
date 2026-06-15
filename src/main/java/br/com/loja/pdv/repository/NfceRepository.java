package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Nfce;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NfceRepository extends JpaRepository<Nfce, Long> {
    Optional<Nfce> findByVendaId(Long vendaId);
    Optional<Nfce> findByRef(String ref);
}
