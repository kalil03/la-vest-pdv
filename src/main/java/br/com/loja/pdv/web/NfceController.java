package br.com.loja.pdv.web;

import br.com.loja.pdv.domain.Nfce;
import br.com.loja.pdv.repository.NfceRepository;
import br.com.loja.pdv.service.NfceCancelamentoService;
import br.com.loja.pdv.service.NfceConsultaService;
import br.com.loja.pdv.service.RegraNegocioException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Gestão de notas fiscais (NFC-e): listagem/busca por status, cancelamento na
 * SEFAZ (evento) e download do XML autorizado para o contador. A emissão/reemissão
 * continua em {@code POST /api/vendas/{id}/nfce} (idempotente).
 */
@RestController
@RequestMapping("/api/nfce")
public class NfceController {

    private final NfceConsultaService consulta;
    private final NfceCancelamentoService cancelamento;
    private final NfceRepository nfceRepository;

    public NfceController(NfceConsultaService consulta, NfceCancelamentoService cancelamento,
                          NfceRepository nfceRepository) {
        this.consulta = consulta;
        this.cancelamento = cancelamento;
        this.nfceRepository = nfceRepository;
    }

    @GetMapping
    public List<NfceConsultaService.NfceLinha> listar(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String q) {
        return consulta.listar(status, q);
    }

    /** Cancela a NFC-e da venda na SEFAZ (evento). Body: {"justificativa": "..."} (15–255 chars). */
    @PostMapping("/{vendaId}/cancelar")
    public NfceCancelamentoService.Resultado cancelar(@PathVariable Long vendaId,
                                                      @RequestBody Map<String, String> body) {
        return cancelamento.cancelar(vendaId, body.get("justificativa"));
    }

    /** XML de distribuição autorizado (nfeProc) para importar no sistema do contador. */
    @GetMapping("/{id}/xml")
    public ResponseEntity<byte[]> baixarXml(@PathVariable Long id) {
        Nfce nfce = nfceRepository.findById(id)
                .orElseThrow(() -> new RegraNegocioException("NFC-e nº " + id + " não encontrada"));
        if (nfce.getXml() == null || nfce.getXml().isBlank()) {
            throw new RegraNegocioException("XML indisponível para esta nota "
                    + "(emitida antes desta atualização — recuperável só por download na SEFAZ).");
        }
        String nome = (nfce.getChaveAcesso() != null ? nfce.getChaveAcesso() : "nfce-" + id) + ".xml";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(nfce.getXml().getBytes(StandardCharsets.UTF_8));
    }
}
