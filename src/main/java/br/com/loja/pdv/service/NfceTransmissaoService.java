package br.com.loja.pdv.service;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.exception.NfeException;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TEnviNFe;
import br.com.swconsultoria.nfe.schema_4.enviNFe.TProtNFe;
import br.com.swconsultoria.nfe.util.ConstantesUtil;
import org.springframework.stereotype.Service;

/**
 * Assina e transmite a NFC-e já montada à SEFAZ, via Java_NFe (Axis2/SOAP).
 * O certificado vem pronto do chamador (ver {@link NfceEmissaoService}) — aqui
 * só monta o lote, assina e envia; não decide o que fazer com o resultado.
 */
@Service
public class NfceTransmissaoService {

    /** cStat 100 = autorizado o uso da NFC-e. */
    private static final String CSTAT_AUTORIZADO = "100";
    /** 103/105 = lote recebido/em processamento: a SEFAZ ainda não decidiu —
     *  não é rejeição, e retransmitir agora geraria duplicidade. */
    private static final java.util.Set<String> CSTAT_EM_PROCESSAMENTO = java.util.Set.of("103", "105");

    public record Resultado(boolean autorizada, boolean emProcessamento,
                            String cStat, String xMotivo, String protocolo) {}

    private final FiscalProperties fiscal;

    public NfceTransmissaoService(FiscalProperties fiscal) {
        this.fiscal = fiscal;
    }

    public Resultado transmitir(NfceSefazService.NfceMontada montada, Certificado certificado)
            throws CertificadoException, NfeException {
        ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(
                EstadosEnum.valueOf(fiscal.getUf()),
                fiscal.isHomologacao() ? AmbienteEnum.HOMOLOGACAO : AmbienteEnum.PRODUCAO,
                certificado, fiscal.getPastaSchemas());

        TEnviNFe envio = new TEnviNFe();
        envio.getNFe().add(montada.nfe());
        envio.setIdLote("1");
        envio.setIndSinc("1"); // NFC-e é sempre síncrona
        envio.setVersao(ConstantesUtil.VERSAO.NFE);

        TEnviNFe assinado = Nfe.montaNfe(config, envio, true);
        var retorno = Nfe.enviarNfe(config, assinado, DocumentoEnum.NFCE);

        TProtNFe prot = retorno.getProtNFe();
        TProtNFe.InfProt infProt = prot != null ? prot.getInfProt() : null;
        String cStat = infProt != null ? infProt.getCStat() : retorno.getCStat();
        String xMotivo = infProt != null ? infProt.getXMotivo() : retorno.getXMotivo();
        String protocolo = infProt != null ? infProt.getNProt() : null;

        return new Resultado(CSTAT_AUTORIZADO.equals(cStat),
                CSTAT_EM_PROCESSAMENTO.contains(cStat), cStat, xMotivo, protocolo);
    }
}