package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.Venda;
import br.com.loja.pdv.repository.VendaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra a emissão da NFC-e de uma venda. Hoje vai só até montar o XML +
 * chave + QR Code (a fundação gratuita via SEFAZ); a assinatura e a transmissão
 * SOAP entram quando o certificado A1 e o CSC da loja forem configurados.
 *
 * <p>Por isso o resultado carrega um {@link Status} explicando ao operador o que
 * ainda falta — em vez de fingir que a nota foi autorizada.
 */
@Service
public class NfceEmissaoService {

    public enum Status {
        /** Dados do emitente não preenchidos: não dá nem para montar. */
        NAO_CONFIGURADO,
        /** XML montado, mas falta o certificado A1 + CSC para assinar/transmitir. */
        PENDENTE_CERTIFICADO,
        /** Autorizada pela SEFAZ (ainda não implementado nesta fase). */
        AUTORIZADA
    }

    public record Resultado(Status status, String chaveAcesso, String mensagem) {}

    private final VendaRepository vendaRepository;
    private final NfceSefazService sefaz;
    private final FiscalProperties fiscal;

    public NfceEmissaoService(VendaRepository vendaRepository,
                              NfceSefazService sefaz,
                              FiscalProperties fiscal) {
        this.vendaRepository = vendaRepository;
        this.sefaz = sefaz;
        this.fiscal = fiscal;
    }

    @Transactional(readOnly = true)
    public Resultado emitir(Long vendaId) {
        if (fiscal.getCnpj() == null || fiscal.getCnpj().isBlank()) {
            return new Resultado(Status.NAO_CONFIGURADO, null,
                    "Antes de emitir, preencha os dados fiscais do emitente "
                    + "(CNPJ, inscrição estadual e endereço) nas configurações.");
        }

        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new IllegalArgumentException("Venda nº " + vendaId + " não encontrada"));
        if (venda.getCanceladaEm() != null) {
            throw new RegraNegocioException("Venda nº " + vendaId + " foi estornada — não emite NFC-e");
        }
        venda.getItens().size(); // força carregar os itens dentro da transação

        // numeração provisória: enquanto não transmitimos, usamos o id da venda
        // como nNF só para montar/validar o XML. A numeração oficial por série
        // entra junto com a transmissão à SEFAZ.
        var montada = sefaz.montar(venda, vendaId.intValue());

        return new Resultado(Status.PENDENTE_CERTIFICADO, montada.chaveAcesso(),
                "NFC-e montada (chave " + montada.chaveAcesso() + "). Para assinar e "
                + "transmitir à SEFAZ-PR, falta configurar o certificado digital A1 e o CSC.");
    }
}
