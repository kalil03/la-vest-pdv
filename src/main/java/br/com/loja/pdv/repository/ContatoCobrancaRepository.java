package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.ContatoCobranca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContatoCobrancaRepository extends JpaRepository<ContatoCobranca, Long> {

    /** Histórico de contatos de um cliente, mais recente primeiro. */
    List<ContatoCobranca> findByClienteIdOrderByDataDesc(Long clienteId);
}
