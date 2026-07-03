package br.com.loja.pdv.web;

import br.com.loja.pdv.service.FechamentoExportService;
import br.com.loja.pdv.service.FechamentoMensalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Fechamento mensal: um cálculo, três saídas (tela/CSV/PDF consomem o mesmo JSON). */
@RestController
@RequestMapping("/api/fechamento-mensal")
public class FechamentoController {

    private final FechamentoMensalService service;
    private final FechamentoExportService export;
    private final String nomeLoja;

    public FechamentoController(FechamentoMensalService service, FechamentoExportService export,
                                @Value("${loja.nome}") String nomeLoja) {
        this.service = service;
        this.export = export;
        this.nomeLoja = nomeLoja;
    }

    @GetMapping
    public FechamentoMensalService.Fechamento gerar(@RequestParam int ano, @RequestParam int mes) {
        return service.gerar(ano, mes);
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> csv(@RequestParam int ano, @RequestParam int mes) {
        byte[] corpo = export.csv(service.gerar(ano, mes)); // mesmo cálculo da tela
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"fechamento-%d-%02d.csv\"".formatted(ano, mes))
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(corpo);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam int ano, @RequestParam int mes) {
        byte[] corpo = export.pdf(service.gerar(ano, mes), nomeLoja); // mesmo cálculo da tela
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"fechamento-%d-%02d.pdf\"".formatted(ano, mes))
                .contentType(MediaType.APPLICATION_PDF)
                .body(corpo);
    }
}
