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
    private final br.com.loja.pdv.service.VendaConsultaService consultaService;

    public VendaController(VendaService vendaService,
                           br.com.loja.pdv.service.VendaConsultaService consultaService) {
        this.vendaService = vendaService;
        this.consultaService = consultaService;
    }

    /** Listagem para conferência/estorno: nº ou cliente, forma e período. */
    @GetMapping
    public br.com.loja.pdv.service.VendaConsultaService.Pagina listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String forma,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate de,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate ate,
            @RequestParam(defaultValue = "1") int pagina) {
        return consultaService.listar(q, forma, de, ate, pagina);
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

    /** Cancela a venda: devolve estoque e apaga lançamentos — atômico. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id) {
        vendaService.cancelar(id);
    }
}
