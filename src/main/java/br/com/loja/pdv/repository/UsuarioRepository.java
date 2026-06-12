package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLoginIgnoreCaseAndAtivoTrue(String login);
    boolean existsByLoginIgnoreCase(String login);
    List<Usuario> findByAtivoTrueOrderByNome();
}
