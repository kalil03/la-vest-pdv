package br.com.loja.pdv.web;

import br.com.loja.pdv.service.CobrancaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CobrancaController {

    private final CobrancaService service;

    public CobrancaController(CobrancaService service) {
        this.service = service;
    }

    @GetMapping("/api/cobranca")
    public CobrancaService.Resultado listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String ordenar) {
        return service.listar(q, ordenar);
    }
}
