package br.com.loja.pdv.web;

import br.com.loja.pdv.service.CarneService;
import br.com.loja.pdv.web.dto.CarneDTO;
import br.com.loja.pdv.web.dto.ReceberRequest;
import br.com.loja.pdv.web.dto.ReciboRecebimento;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class CarneController {

    private final CarneService carneService;

    public CarneController(CarneService carneService) {
        this.carneService = carneService;
    }

    /** Carnê do cliente: saldo, parcelas em aberto (status FIFO) e histórico. */
    @GetMapping("/api/clientes/{id}/carne")
    public CarneDTO carne(@PathVariable Long id) {
        return carneService.montar(id);
    }

    /** Recebimento de carnê — atômico, com recibo pronto para impressão. */
    @PostMapping("/api/recebimentos")
    @ResponseStatus(HttpStatus.CREATED)
    public ReciboRecebimento receber(@RequestBody @Valid ReceberRequest req) {
        return carneService.receber(req);
    }
}
