package br.com.loja.pdv.web;

import br.com.loja.pdv.service.ProdutoService;
import br.com.loja.pdv.web.dto.NovoProdutoRequest;
import br.com.loja.pdv.web.dto.ProdutoDTO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    @GetMapping
    public List<ProdutoDTO> buscar(@RequestParam(defaultValue = "") String q,
                                   @RequestParam(required = false) Long marcaId,
                                   @RequestParam(defaultValue = "") String categoria,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte) {
        return produtoService.buscar(q, marcaId, categoria, dataDe, dataAte);
    }

    @GetMapping("/categorias")
    public List<String> categorias() {
        return produtoService.categorias();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoDTO criar(@RequestBody @Valid NovoProdutoRequest req) {
        return produtoService.criar(req);
    }

    @PutMapping("/{id}")
    public ProdutoDTO atualizar(@PathVariable Long id, @RequestBody @Valid NovoProdutoRequest req) {
        return produtoService.atualizar(id, req);
    }

    /** Produtos (com dados fiscais) de uma venda — para a tela de correção da NFC-e. */
    @GetMapping("/da-venda/{vendaId}")
    public List<ProdutoService.ProdutoFiscal> daVenda(@PathVariable Long vendaId) {
        return produtoService.produtosDaVenda(vendaId);
    }

    /** Corrige só os campos fiscais do produto (NCM, CFOP, CSOSN, unidade, origem, CEST). */
    @PatchMapping("/{id}/fiscal")
    public ProdutoService.ProdutoFiscal atualizarFiscal(
            @PathVariable Long id, @RequestBody ProdutoService.ProdutoFiscalUpdate req) {
        return produtoService.atualizarFiscal(id, req);
    }
}
