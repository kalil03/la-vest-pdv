package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.Marca;
import br.com.loja.pdv.domain.Produto;
import br.com.loja.pdv.domain.Variacao;
import br.com.loja.pdv.repository.MarcaRepository;
import br.com.loja.pdv.repository.ProdutoRepository;
import br.com.loja.pdv.web.dto.NovoProdutoRequest;
import br.com.loja.pdv.web.dto.ProdutoDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ProdutoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final ProdutoRepository produtoRepository;
    private final MarcaRepository marcaRepository;

    public ProdutoService(ProdutoRepository produtoRepository, MarcaRepository marcaRepository) {
        this.produtoRepository = produtoRepository;
        this.marcaRepository = marcaRepository;
    }

    @Transactional
    public ProdutoDTO criar(NovoProdutoRequest req) {
        Produto produto = new Produto();
        produto.setCodigo(resolverCodigoNovo(req.codigo()));
        aplicar(produto, req);
        aplicarVariacoes(produto, req);
        produtoRepository.save(produto);
        return ProdutoDTO.de(produto);
    }

    @Transactional
    public ProdutoDTO atualizar(Long id, NovoProdutoRequest req) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Produto não encontrado (id " + id + ")"));

        if (!emBranco(req.codigo()) && !req.codigo().trim().equals(produto.getCodigo())) {
            if (produtoRepository.existsByCodigo(req.codigo().trim())) {
                throw new RegraNegocioException("Já existe um produto com o código " + req.codigo().trim());
            }
            produto.setCodigo(req.codigo().trim());
        }
        aplicar(produto, req);
        // Variações não são trocadas na edição: itens de vendas antigas apontam
        // para elas. Acrescentar/ajustar grade fica para uma tela própria.
        return ProdutoDTO.de(produto);
    }

    /** Campos comuns a criação e edição. */
    private void aplicar(Produto produto, NovoProdutoRequest req) {
        produto.setNome(req.nome().trim());
        produto.setCategoria(limpar(req.categoria()));
        produto.setPreco(req.preco());
        produto.setMarca(resolverMarca(req.marcaNome()));
        produto.setNcm(limpar(req.ncm()));
        produto.setCest(limpar(req.cest()));
        produto.setUnidade(emBranco(req.unidade()) ? "UN" : req.unidade().trim().toUpperCase());
        produto.setCodigoBarras(limpar(req.codigoBarras()));
        produto.setOrigem(req.origem() != null ? req.origem() : 0);
        produto.setPrecoCusto(req.pCusto());
        produto.setPLucro(req.pLucro());
        produto.setPrecoVenda2(req.pAtacado());
        produto.setPLucro2(req.pLucroAtacado());
        
        produto.setQtdeMin(req.estMinimo());
    }

    private void aplicarVariacoes(Produto produto, NovoProdutoRequest req) {
        if (req.variacoes() == null || req.variacoes().isEmpty()) {
            // Produto sem grade (perfume): variação única "padrão", escondida na interface
            produto.adicionarVariacao(new Variacao());
        } else {
            for (NovoProdutoRequest.NovaVariacao nv : req.variacoes()) {
                Variacao v = new Variacao();
                v.setTamanho(limpar(nv.tamanho()));
                v.setCor(limpar(nv.cor()));
                produto.adicionarVariacao(v);
            }
        }
    }

    /** Marca é criada na hora se não existir — sem tela extra obrigatória. */
    private Marca resolverMarca(String nome) {
        if (emBranco(nome)) return null;
        String limpo = nome.trim();
        return marcaRepository.findByNomeIgnoreCase(limpo).orElseGet(() -> {
            Marca nova = new Marca();
            nova.setNome(limpo);
            return marcaRepository.save(nova);
        });
    }

    @Transactional(readOnly = true)
    public List<ProdutoDTO> buscar(String q, Long marcaId, String categoria, LocalDate dataDe, LocalDate dataAte) {
        Instant de = dataDe != null ? dataDe.atStartOfDay(FUSO).toInstant() : null;
        Instant ate = dataAte != null ? dataAte.plusDays(1).atStartOfDay(FUSO).toInstant() : null;
        return produtoRepository.buscar(
                        q == null ? "" : q.trim(),
                        marcaId,
                        categoria == null ? "" : categoria.trim(),
                        de, ate).stream()
                .map(ProdutoDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> categorias() {
        return produtoRepository.categorias();
    }

    /**
     * Códigos legados do SET (que os funcionários decoraram) são preservados;
     * produto novo sem código ganha um sequencial a partir de 100000.
     */
    private String resolverCodigoNovo(String codigo) {
        if (emBranco(codigo)) {
            return String.valueOf(produtoRepository.proximoCodigoGerado());
        }
        String limpo = codigo.trim();
        if (produtoRepository.existsByCodigo(limpo)) {
            throw new RegraNegocioException("Já existe um produto com o código " + limpo);
        }
        return limpo;
    }

    private static String limpar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static boolean emBranco(String s) {
        return s == null || s.isBlank();
    }
}
