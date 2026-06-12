package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.Variacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VariacaoRepository extends JpaRepository<Variacao, Long> {

    /**
     * Baixa de estoque atômica: um único UPDATE no banco, sem ler-modificar-gravar
     * em Java (que poderia perder atualização concorrente). Retorna 0 se a
     * variação não existe — o serviço usa isso para abortar a venda.
     *
     * O estoque PODE ficar negativo de propósito: depois de 1 ano sem baixa,
     * o estoque do sistema estará errado; bloquear a venda por causa disso
     * faria os funcionários voltarem ao papel. Negativo = "inventário a acertar".
     */
    @Modifying
    @Query("UPDATE Variacao v SET v.estoque = v.estoque - :quantidade WHERE v.id = :id")
    int baixarEstoque(@Param("id") Long id, @Param("quantidade") int quantidade);

    /** Cancelamento de venda: devolve o que a venda tinha baixado. */
    @Modifying
    @Query("UPDATE Variacao v SET v.estoque = v.estoque + :quantidade WHERE v.id = :id")
    int devolverEstoque(@Param("id") Long id, @Param("quantidade") int quantidade);
}
