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
import org.springframework.stereotype.Service;

/**
 * Assina e transmite a NFC-e já montada à SEFAZ, via Java_NFe (Axis2/SOAP).
 * O certificado vem pronto do chamador (ver {@link NfceEmissaoService}) — aqui
 * só monta o lote, assina e envia; não decide o que fazer com o resultado.
 */
@Service
public class NfceTransmissaoService {

    /** cStat 100 = autorizado o uso da NFC-e. Qualquer outro é rejeição/erro da SEFAZ. */
    private static final String CSTAT_AUTORIZADO = "100";

    public record Resultado(boolean autorizada, String cStat, String xMotivo, String protocolo) {}

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
        preencherEnvelope(envio);

        TEnviNFe assinado = Nfe.montaNfe(config, envio, true);
        var retorno = Nfe.enviarNfe(config, assinado, DocumentoEnum.NFCE);

        TProtNFe prot = retorno.getProtNFe();
        String cStat = prot != null ? prot.getInfProt().getCStat() : retorno.getCStat();
        String xMotivo = prot != null ? prot.getInfProt().getXMotivo() : retorno.getXMotivo();
        String protocolo = prot != null ? prot.getInfProt().getNProt() : null;

        return new Resultado(CSTAT_AUTORIZADO.equals(cStat), cStat, xMotivo, protocolo);
    }

    /**
     * {@link TEnviNFe#idLote}, {@code indSinc} e {@code versao} são campos
     * protected sem setter público nesta versão da lib — só dá pra preencher
     * via reflexão. NFC-e exige indSinc=1 (síncrona).
     */
    private void preencherEnvelope(TEnviNFe envio) {
        try {
            setCampo(envio, "idLote", "1");
            setCampo(envio, "indSinc", "1");
            setCampo(envio, "versao", br.com.swconsultoria.nfe.util.ConstantesUtil.VERSAO.NFE);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Falha ao montar o envelope da NFC-e: " + e.getMessage(), e);
        }
    }

    private void setCampo(Object alvo, String campo, String valor) throws ReflectiveOperationException {
        var field = TEnviNFe.class.getDeclaredField(campo);
        field.setAccessible(true);
        field.set(alvo, valor);
    }
}
