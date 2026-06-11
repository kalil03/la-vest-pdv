package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Busca por código exato OU nome parcial (sem diferenciar acento/caixa).
     * Quem digitou o código exato vê esse produto em primeiro lugar.
     * Query nativa porque unaccent/ILIKE são recursos do PostgreSQL.
     */
    @Query(value = """
            SELECT p.* FROM produto p
            WHERE p.codigo = :q
               OR unaccent(p.nome) ILIKE unaccent('%' || :q || '%')
            ORDER BY (p.codigo = :q) DESC, p.nome
            LIMIT 20
            """, nativeQuery = true)
    List<Produto> buscar(@Param("q") String q);

    Optional<Produto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Query(value = "SELECT nextval('produto_codigo_seq')", nativeQuery = true)
    long proximoCodigoGerado();
}
