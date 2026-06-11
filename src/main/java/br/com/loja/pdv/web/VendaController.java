package br.com.loja.pdv.web;

import br.com.loja.pdv.service.VendaService;
import br.com.loja.pdv.web.dto.FecharVendaRequest;
import br.com.loja.pdv.web.dto.VendaResumo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vendas")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    /** O clique único: grava venda + itens, baixa estoque e (se fiado) lança no carnê. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VendaResumo fechar(@RequestBody @Valid FecharVendaRequest req) {
        return vendaService.fechar(req);
    }

    /** Reimpressão da notinha. */
    @GetMapping("/{id}")
    public VendaResumo resumo(@PathVariable Long id) {
        return vendaService.buscarResumo(id);
    }
}
