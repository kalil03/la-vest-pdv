package br.com.loja.pdv.web;

import br.com.loja.pdv.service.ProdutoService;
import br.com.loja.pdv.web.dto.NovoProdutoRequest;
import br.com.loja.pdv.web.dto.ProdutoDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService produtoService;

    public ProdutoController(ProdutoService produtoService) {
        this.produtoService = produtoService;
    }

    /** Busca por código exato ou nome parcial; retorna variações com estoque. */
    @GetMapping
    public List<ProdutoDTO> buscar(@RequestParam(defaultValue = "") String q) {
        return produtoService.buscar(q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoDTO criar(@RequestBody @Valid NovoProdutoRequest req) {
        return produtoService.criar(req);
    }
}
