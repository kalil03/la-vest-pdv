package br.com.loja.pdv.web;

import br.com.loja.pdv.service.CondicionalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/condicionais")
public class CondicionalController {

    private final CondicionalService service;

    public CondicionalController(CondicionalService service) {
        this.service = service;
    }

    @GetMapping
    public List<CondicionalService.Resumo> listar(@RequestParam(defaultValue = "ABERTA") String status) {
        return service.listar(status);
    }

    @GetMapping("/{id}")
    public CondicionalService.CondicionalDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public CondicionalService.CondicionalDTO abrir(@RequestBody CondicionalService.AbrirRequest req) {
        return service.abrir(req);
    }

    @PostMapping("/{id}/fechar")
    public ResponseEntity<Void> fechar(@PathVariable Long id, @RequestParam Long vendaId) {
        service.marcarFechada(id, vendaId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        service.cancelar(id);
        return ResponseEntity.ok().build();
    }
}
