package br.com.loja.pdv.web.dto;

import br.com.loja.pdv.domain.Produto;
import br.com.loja.pdv.domain.Variacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProdutoDTO(
        Long id,
        String codigo,
        String nome,
        String categoria,
        Long marcaId,
        String marcaNome,
        BigDecimal preco,
        Instant dataCriacao,
        String ncm,
        String cest,
        String unidade,
        String codigoBarras,
        Integer origem,
        BigDecimal pCusto,
        BigDecimal pLucro,
        BigDecimal pAtacado,
        BigDecimal pLucroAtacado,
        BigDecimal estoque,
        BigDecimal estMinimo,
        List<VariacaoDTO> variacoes) {

    public record VariacaoDTO(Long id, String tamanho, String cor, boolean padrao) {
        public static VariacaoDTO de(Variacao v) {
            return new VariacaoDTO(v.getId(), v.getTamanho(), v.getCor(), v.isPadrao());
        }
    }

    public static ProdutoDTO de(Produto p) {
        return new ProdutoDTO(p.getId(), p.getCodigo(), p.getNome(), p.getCategoria(),
                p.getMarca() != null ? p.getMarca().getId() : null,
                p.getMarca() != null ? p.getMarca().getNome() : null,
                p.getPreco(), p.getDataCriacao(),
                p.getNcm(), p.getCest(), p.getUnidade(), p.getCodigoBarras(), p.getOrigem(),
                p.getPrecoCusto(), p.getPLucro(), p.getPrecoVenda2(), p.getPLucro2(), null, p.getQtdeMin(),
                p.getVariacoes().stream().map(VariacaoDTO::de).toList());
    }
}
