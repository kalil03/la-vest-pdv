package br.com.loja.pdv.web;

import br.com.loja.pdv.service.BaixaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/baixas")
public class BaixaController {

    private final BaixaService service;

    public BaixaController(BaixaService service) {
        this.service = service;
    }

    @GetMapping
    public List<BaixaService.BaixaDTO> listar(@RequestParam(defaultValue = "ATIVA") String status) {
        return service.listar(status);
    }

    @PostMapping
    public BaixaService.BaixaDTO darBaixa(@RequestBody BaixaService.DarBaixaRequest req) {
        return service.darBaixa(req);
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable Long id,
                                          @RequestParam(required = false) String operador) {
        service.restaurar(id, operador);
        return ResponseEntity.ok().build();
    }
}
