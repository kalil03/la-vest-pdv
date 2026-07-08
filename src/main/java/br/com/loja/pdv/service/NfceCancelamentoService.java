package br.com.loja.pdv.service;

import br.com.loja.pdv.Fuso;
import br.com.loja.pdv.domain.Nfce;
import br.com.loja.pdv.repository.NfceRepository;
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.DocumentoEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.schema.envEventoCancNFe.TEnvEvento;
import br.com.swconsultoria.nfe.schema.envEventoCancNFe.TEvento;
import br.com.swconsultoria.nfe.schema.envEventoCancNFe.TRetEnvEvento;
import br.com.swconsultoria.nfe.schema.envEventoCancNFe.TRetEvento;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Cancelamento de NFC-e via evento (tpEvento 110111) na SEFAZ. Exige justificativa
 * de 15 a 255 caracteres e uma nota AUTORIZADA (chave + protocolo). A SEFAZ só
 * aceita dentro da janela legal de cancelamento; fora dela devolve rejeição —
 * este serviço não decide isso, apenas transmite e reporta o que a SEFAZ respondeu.
 * Cancelar a NFC-e NÃO estorna a venda (estoque/fiado): são operações separadas.
 */
@Service
public class NfceCancelamentoService {

    private static final String TP_EVENTO_CANCELAMENTO = "110111";
    private static final String VERSAO_EVENTO = "1.00";
    /** Evento registrado: 135 (homologado), 136 (vinculado), 155 (registrado fora de prazo). */
    private static final Set<String> CSTAT_OK = Set.of("135", "136", "155");

    private final NfceRepository nfceRepository;
    private final FiscalProperties fiscal;

    public NfceCancelamentoService(NfceRepository nfceRepository, FiscalProperties fiscal) {
        this.nfceRepository = nfceRepository;
        this.fiscal = fiscal;
    }

    public record Resultado(boolean cancelada, String mensagem) {}

    @Transactional
    public Resultado cancelar(Long vendaId, String justificativa) {
        if (justificativa == null || justificativa.trim().length() < 15) {
            throw new RegraNegocioException("A justificativa do cancelamento precisa de pelo menos 15 caracteres");
        }
        String just = justificativa.trim();
        if (just.length() > 255) just = just.substring(0, 255);

        Nfce nfce = nfceRepository.findByVendaId(vendaId)
                .orElseThrow(() -> new RegraNegocioException("Venda nº " + vendaId + " não tem NFC-e emitida"));
        if (nfce.getStatus() == Nfce.Status.CANCELADO) {
            return new Resultado(true, "Esta NFC-e já estava cancelada");
        }
        if (nfce.getStatus() != Nfce.Status.AUTORIZADO
                || nfce.getChaveAcesso() == null || nfce.getProtocolo() == null) {
            throw new RegraNegocioException("Só é possível cancelar uma NFC-e AUTORIZADA (com chave e protocolo)");
        }

        Certificado certificado;
        try {
            certificado = fiscal.temCertificadoArquivo()
                    ? CertificadoService.certificadoPfx(fiscal.getCertificadoCaminho(), fiscal.getCertificadoSenha())
                    : CertificadoService.getCertificadoByCnpjCpf(fiscal.getCnpj());
        } catch (CertificadoException | java.io.FileNotFoundException e) {
            throw new RegraNegocioException("Não consegui carregar o certificado A1 para cancelar: " + e.getMessage());
        }

        EstadosEnum estado = EstadosEnum.valueOf(fiscal.getUf());
        ConfiguracoesNfe config;
        try {
            config = ConfiguracoesNfe.criarConfiguracoes(estado,
                    fiscal.isHomologacao() ? AmbienteEnum.HOMOLOGACAO : AmbienteEnum.PRODUCAO,
                    certificado, fiscal.getPastaSchemas());
        } catch (CertificadoException e) {
            throw new RegraNegocioException("Falha ao preparar o ambiente de cancelamento: " + e.getMessage());
        }

        TRetEnvEvento retorno;
        try {
            retorno = Nfe.cancelarNfe(config, montarEvento(estado, nfce, just), true, DocumentoEnum.NFCE);
        } catch (Exception e) {
            throw new RegraNegocioException("Falha ao transmitir o cancelamento à SEFAZ: " + e.getMessage());
        }

        TRetEvento.InfEvento inf = retorno.getRetEvento() != null && !retorno.getRetEvento().isEmpty()
                ? retorno.getRetEvento().get(0).getInfEvento() : null;
        String cStat = inf != null ? inf.getCStat() : retorno.getCStat();
        String xMotivo = inf != null ? inf.getXMotivo() : retorno.getXMotivo();

        if (cStat != null && CSTAT_OK.contains(cStat)) {
            nfce.setStatus(Nfce.Status.CANCELADO);
            nfce.setMensagem("Cancelada [" + cStat + "] " + xMotivo + " — " + just);
            if (inf != null && inf.getNProt() != null) nfce.setProtocolo(inf.getNProt());
            nfceRepository.save(nfce);
            return new Resultado(true, "NFC-e cancelada na SEFAZ (" + cStat + " — " + xMotivo + ")");
        }
        return new Resultado(false, "SEFAZ não cancelou: [" + cStat + "] " + xMotivo);
    }

    private TEnvEvento montarEvento(EstadosEnum estado, Nfce nfce, String justificativa) {
        String chave = nfce.getChaveAcesso();
        String dhEvento = XmlNfeUtil.dataNfe(LocalDateTime.now(Fuso.LOJA), Fuso.LOJA);

        TEvento.InfEvento.DetEvento det = new TEvento.InfEvento.DetEvento();
        det.setVersao(VERSAO_EVENTO);
        det.setDescEvento("Cancelamento");
        det.setNProt(nfce.getProtocolo());
        det.setXJust(justificativa);

        TEvento.InfEvento inf = new TEvento.InfEvento();
        inf.setCOrgao(estado.getCodigoUF());
        inf.setTpAmb(fiscal.tpAmb());
        inf.setCNPJ(fiscal.getCnpj());
        inf.setChNFe(chave);
        inf.setDhEvento(dhEvento);
        inf.setTpEvento(TP_EVENTO_CANCELAMENTO);
        inf.setNSeqEvento("1");
        inf.setVerEvento(VERSAO_EVENTO);
        inf.setDetEvento(det);
        inf.setId("ID" + TP_EVENTO_CANCELAMENTO + chave + "01");

        TEvento evento = new TEvento();
        evento.setVersao(VERSAO_EVENTO);
        evento.setInfEvento(inf);

        TEnvEvento env = new TEnvEvento();
        env.setVersao(VERSAO_EVENTO);
        env.setIdLote("1");
        env.getEvento().add(evento);
        return env;
    }
}
