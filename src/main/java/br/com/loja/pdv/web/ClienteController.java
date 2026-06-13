package br.com.loja.pdv.web;

import br.com.loja.pdv.service.ClienteService;
import br.com.loja.pdv.web.dto.ClienteDTO;
import br.com.loja.pdv.web.dto.NovoClienteRequest;
import br.com.loja.pdv.web.dto.ScoreCliente;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteDTO> buscar(@RequestParam(defaultValue = "") String q) {
        return clienteService.buscar(q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDTO criar(@RequestBody @Valid NovoClienteRequest req) {
        return clienteService.criar(req);
    }

    @PutMapping("/{id}")
    public ClienteDTO atualizar(@PathVariable Long id, @RequestBody @Valid NovoClienteRequest req) {
        return clienteService.atualizar(id, req);
    }

    @GetMapping("/{id}/score")
    public ScoreCliente score(@PathVariable Long id) {
        return clienteService.score(id);
    }

    @GetMapping("/por-venda/{vendaId}")
    public ClienteDTO porVenda(@PathVariable Long vendaId) {
        return clienteService.porVenda(vendaId);
    }
}
