package br.com.loja.pdv.web;

import br.com.loja.pdv.service.ContasReceberService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
public class ContasReceberController {

    private final ContasReceberService service;

    public ContasReceberController(ContasReceberService service) {
        this.service = service;
    }

    @GetMapping("/api/contas-receber")
    public ContasReceberService.Pagina listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(defaultValue = "1") int pagina) {
        return service.listar(q, status, de, ate, pagina);
    }
}
