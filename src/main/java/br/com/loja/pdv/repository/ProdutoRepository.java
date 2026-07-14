package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /**
     * Busca com filtros combináveis: texto (código exato, código de barras
     * exato ou nome parcial sem acento), marca, categoria e período de cadastro.
     * Filtro nulo/vazio é ignorado. Quem digitou o código exato vê o produto primeiro.
     */
    @Query(value = """
            SELECT p.* FROM produto p
            WHERE (:q = '' OR p.codigo = :q OR p.codigo_barras = :q
                   OR TRIM(LEADING '0' FROM p.codigo) = TRIM(LEADING '0' FROM :q)
                   OR unaccent(p.nome) ILIKE unaccent('%' || :q || '%'))
              AND (CAST(:marcaId AS bigint) IS NULL OR p.marca_id = :marcaId)
              AND (:categoria = '' OR p.categoria = :categoria)
              AND (CAST(:dataDe AS timestamptz) IS NULL OR p.data_criacao >= :dataDe)
              AND (CAST(:dataAte AS timestamptz) IS NULL OR p.data_criacao <= :dataAte)
            ORDER BY (p.codigo = :q
                      OR TRIM(LEADING '0' FROM p.codigo) = TRIM(LEADING '0' FROM :q)) DESC, p.nome
            LIMIT 200
            """, nativeQuery = true)
    List<Produto> buscar(@Param("q") String q,
                         @Param("marcaId") Long marcaId,
                         @Param("categoria") String categoria,
                         @Param("dataDe") Instant dataDe,
                         @Param("dataAte") Instant dataAte);

    Optional<Produto> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    @Query(value = "SELECT DISTINCT categoria FROM produto WHERE categoria IS NOT NULL ORDER BY categoria", nativeQuery = true)
    List<String> categorias();

    /**
     * Menor número livre para código de produto novo (1, 2, 3…) — curto de
     * digitar no caixa. A comparação é NUMÉRICA, então não colide nem com os
     * códigos legados do SET com zeros à esquerda (gerar "1504" quando existe
     * "001504" confundiria a busca).
     */
    @Query(value = """
            SELECT MIN(n) FROM generate_series(1, 99999) n
            WHERE NOT EXISTS (
                SELECT 1 FROM produto
                WHERE codigo ~ '^[0-9]+$' AND CAST(codigo AS bigint) = n)
            """, nativeQuery = true)
    long proximoCodigoGerado();
}
