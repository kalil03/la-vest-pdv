package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.Produto;
import br.com.loja.pdv.domain.Variacao;
import br.com.loja.pdv.repository.ProdutoRepository;
import br.com.loja.pdv.web.dto.NovoProdutoRequest;
import br.com.loja.pdv.web.dto.ProdutoDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @Transactional
    public ProdutoDTO criar(NovoProdutoRequest req) {
        Produto produto = new Produto();
        produto.setCodigo(resolverCodigo(req.codigo()));
        produto.setNome(req.nome().trim());
        produto.setCategoria(emBranco(req.categoria()) ? null : req.categoria().trim());
        produto.setPreco(req.preco());

        if (req.variacoes() == null || req.variacoes().isEmpty()) {
            // Produto sem grade (perfume): variação única "padrão", escondida na interface
            produto.adicionarVariacao(new Variacao());
        } else {
            for (NovoProdutoRequest.NovaVariacao nv : req.variacoes()) {
                Variacao v = new Variacao();
                v.setTamanho(emBranco(nv.tamanho()) ? null : nv.tamanho().trim());
                v.setCor(emBranco(nv.cor()) ? null : nv.cor().trim());
                v.setEstoque(nv.estoque());
                produto.adicionarVariacao(v);
            }
        }

        produtoRepository.save(produto);
        return ProdutoDTO.de(produto);
    }

    @Transactional(readOnly = true)
    public List<ProdutoDTO> buscar(String q) {
        // Com q em branco, o ILIKE '%%' casa com tudo e o LIMIT 20 segura o volume
        return produtoRepository.buscar(q == null ? "" : q.trim()).stream()
                .map(ProdutoDTO::de)
                .toList();
    }

    /**
     * Códigos legados do SET (que os funcionários decoraram) são preservados;
     * produto novo sem código ganha um sequencial a partir de 100000.
     */
    private String resolverCodigo(String codigo) {
        if (emBranco(codigo)) {
            return String.valueOf(produtoRepository.proximoCodigoGerado());
        }
        String limpo = codigo.trim();
        if (produtoRepository.existsByCodigo(limpo)) {
            throw new RegraNegocioException("Já existe um produto com o código " + limpo);
        }
        return limpo;
    }

    private static boolean emBranco(String s) {
        return s == null || s.isBlank();
    }
}
