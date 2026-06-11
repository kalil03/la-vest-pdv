package br.com.loja.pdv.web;

import br.com.loja.pdv.service.ClienteService;
import br.com.loja.pdv.web.dto.ClienteDTO;
import br.com.loja.pdv.web.dto.ScoreCliente;
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

    /** "Score da casa": saldo devedor calculado + prazo médio de pagamento. */
    @GetMapping("/{id}/score")
    public ScoreCliente score(@PathVariable Long id) {
        return clienteService.score(id);
    }
}
