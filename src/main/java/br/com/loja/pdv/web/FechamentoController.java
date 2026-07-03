package br.com.loja.pdv.web;

import br.com.loja.pdv.service.FechamentoMensalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fechamento mensal: um cálculo, três saídas (tela/CSV/PDF consomem o mesmo JSON). */
@RestController
@RequestMapping("/api/fechamento-mensal")
public class FechamentoController {

    private final FechamentoMensalService service;

    public FechamentoController(FechamentoMensalService service) {
        this.service = service;
    }

    @GetMapping
    public FechamentoMensalService.Fechamento gerar(@RequestParam int ano, @RequestParam int mes) {
        return service.gerar(ano, mes);
    }
}
