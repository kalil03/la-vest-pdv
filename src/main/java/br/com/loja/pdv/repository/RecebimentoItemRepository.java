package br.com.loja.pdv.repository;

import br.com.loja.pdv.domain.RecebimentoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecebimentoItemRepository extends JpaRepository<RecebimentoItem, Long> {

    List<RecebimentoItem> findByPagamentoId(Long pagamentoId);

    void deleteByPagamentoId(Long pagamentoId);
}
