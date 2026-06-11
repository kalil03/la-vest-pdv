package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.Produto;
import br.com.loja.pdv.domain.Variacao;

import java.math.BigDecimal;
import java.util.List;

public record ProdutoDTO(
        Long id,
        String codigo,
        String nome,
        String categoria,
        BigDecimal preco,
        List<VariacaoDTO> variacoes) {

    public record VariacaoDTO(Long id, String tamanho, String cor, int estoque, boolean padrao) {
        public static VariacaoDTO de(Variacao v) {
            return new VariacaoDTO(v.getId(), v.getTamanho(), v.getCor(), v.getEstoque(), v.isPadrao());
        }
    }

    public static ProdutoDTO de(Produto p) {
        return new ProdutoDTO(p.getId(), p.getCodigo(), p.getNome(), p.getCategoria(), p.getPreco(),
                p.getVariacoes().stream().map(VariacaoDTO::de).toList());
    }
}
