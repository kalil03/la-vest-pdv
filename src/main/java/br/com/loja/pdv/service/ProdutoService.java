package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.Marca;
import br.com.loja.pdv.domain.Produto;
import br.com.loja.pdv.domain.Variacao;
import br.com.loja.pdv.repository.MarcaRepository;
import br.com.loja.pdv.repository.ProdutoRepository;
import br.com.loja.pdv.web.dto.NovoProdutoRequest;
import br.com.loja.pdv.web.dto.ProdutoDTO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ProdutoService {

    private static final ZoneId FUSO = br.com.loja.pdv.Fuso.LOJA;

    private final ProdutoRepository produtoRepository;
    private final MarcaRepository marcaRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public ProdutoService(ProdutoRepository produtoRepository, MarcaRepository marcaRepository,
                          NamedParameterJdbcTemplate jdbc) {
        this.produtoRepository = produtoRepository;
        this.marcaRepository = marcaRepository;
        this.jdbc = jdbc;
    }

    /** Dados fiscais de um produto (o que costuma travar a NFC-e). */
    public record ProdutoFiscal(Long id, String codigo, String nome, String ncm, String cfop,
                                String csosn, String unidade, Integer origem, String cest) {}

    public record ProdutoFiscalUpdate(String ncm, String cfop, String csosn, String unidade,
                                      Integer origem, String cest) {}

    /** Produtos distintos que compõem uma venda, com seus campos fiscais — para a
     *  tela de correção/reemissão da NFC-e. */
    @Transactional(readOnly = true)
    public List<ProdutoFiscal> produtosDaVenda(Long vendaId) {
        return jdbc.query("""
                SELECT DISTINCT p.id, p.codigo, p.nome, p.ncm, p.cfop, p.csosn, p.unidade, p.origem, p.cest
                FROM item_venda iv
                JOIN variacao v ON v.id = iv.variacao_id
                JOIN produto p ON p.id = v.produto_id
                WHERE iv.venda_id = :vendaId
                ORDER BY p.codigo
                """, new MapSqlParameterSource("vendaId", vendaId),
                (rs, i) -> new ProdutoFiscal(rs.getLong("id"), rs.getString("codigo"), rs.getString("nome"),
                        rs.getString("ncm"), rs.getString("cfop"), rs.getString("csosn"),
                        rs.getString("unidade"), (Integer) rs.getObject("origem"), rs.getString("cest")));
    }

    /** Atualiza SÓ os campos fiscais do produto (não mexe em preço/variações).
     *  CFOP/CSOSN em branco voltam a null → usam o padrão da loja (5102/102). */
    @Transactional
    public ProdutoFiscal atualizarFiscal(Long id, ProdutoFiscalUpdate req) {
        Produto p = produtoRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("Produto não encontrado (id " + id + ")"));
        // NCM opcional: a nota fiscal é emitida pelo Set, não por este sistema
        p.setNcm(limpar(req.ncm()));
        p.setCfop(limpar(req.cfop()));
        p.setCsosn(limpar(req.csosn()));
        if (!emBranco(req.unidade())) p.setUnidade(req.unidade().trim().toUpperCase());
        if (req.origem() != null) p.setOrigem(req.origem());
        p.setCest(limpar(req.cest()));
        return new ProdutoFiscal(p.getId(), p.getCodigo(), p.getNome(), p.getNcm(), p.getCfop(),
                p.getCsosn(), p.getUnidade(), p.getOrigem(), p.getCest());
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
     * produto novo sem código ganha o MENOR número livre (1, 2, 3…) — fácil de
     * digitar no caixa. Ver ProdutoRepository.proximoCodigoGerado.
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
