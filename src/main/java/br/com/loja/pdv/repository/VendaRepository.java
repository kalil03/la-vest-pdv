package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    /**
     * Marcação atômica de cancelamento (padrão do baixarEstoque): o WHERE
     * garante que só o primeiro estorno passa — 0 linhas = já estava
     * cancelada, e o serviço aborta antes de devolver estoque em dobro.
     */
    @Modifying
    @Query("""
            UPDATE Venda v SET v.canceladaEm = :agora, v.canceladaPor = :operador,
                   v.cancelamentoMotivo = :motivo
            WHERE v.id = :id AND v.canceladaEm IS NULL
            """)
    int marcarCancelada(@Param("id") Long id, @Param("agora") Instant agora,
                        @Param("operador") String operador, @Param("motivo") String motivo);
}
