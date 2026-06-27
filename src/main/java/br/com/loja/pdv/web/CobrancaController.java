package br.com.loja.pdv.web;

import br.com.loja.pdv.service.CobrancaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class CobrancaController {

    private final CobrancaService service;

    public CobrancaController(CobrancaService service) {
        this.service = service;
    }

    @GetMapping("/api/cobranca")
    public CobrancaService.Resultado listar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String ordenar,
            @RequestParam(defaultValue = "") String tipo) {
        return service.listar(q, ordenar, tipo);
    }

    @PostMapping("/api/cobranca/contato")
    public ResponseEntity<Void> registrarContato(@RequestBody CobrancaService.RegistrarContatoRequest req) {
        service.registrarContato(req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/cobranca/contatos/{clienteId}")
    public List<CobrancaService.Contato> contatos(@PathVariable Long clienteId) {
        return service.listarContatos(clienteId);
    }
}
