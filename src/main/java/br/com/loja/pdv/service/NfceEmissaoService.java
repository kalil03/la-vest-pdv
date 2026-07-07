package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.Nfce;
import br.com.loja.pdv.domain.Venda;
import br.com.loja.pdv.repository.NfceRepository;
import br.com.loja.pdv.repository.VendaRepository;
import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.certificado.exception.CertificadoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Orquestra a emissão da NFC-e de uma venda: monta o XML, assina e transmite
 * à SEFAZ e guarda o resultado na tabela {@code nfce}. O certificado A1 vem
 * do arquivo .pfx configurado (fiscal.certificado-caminho/senha); sem isso,
 * cai para o repositório do Windows (sem senha, mas com um bug conhecido da
 * lib pra assinar a partir de lá — ver NfceTransmissaoService).
 */
@Service
public class NfceEmissaoService {

    public enum Status {
        /** Dados do emitente não preenchidos: não dá nem para montar. */
        NAO_CONFIGURADO,
        /** XML montado, mas o certificado A1 não foi encontrado no Windows. */
        PENDENTE_CERTIFICADO,
        /** Autorizada pela SEFAZ. */
        AUTORIZADA,
        /** SEFAZ recebeu e rejeitou (cStat != 100). */
        REJEITADA,
        /** Falha de rede/transmissão antes de obter resposta da SEFAZ. */
        ERRO_TRANSMISSAO
    }

    public record Resultado(Status status, String chaveAcesso, String mensagem) {}

    private final VendaRepository vendaRepository;
    private final NfceRepository nfceRepository;
    private final NfceSefazService sefaz;
    private final NfceTransmissaoService transmissao;
    private final FiscalProperties fiscal;

    public NfceEmissaoService(VendaRepository vendaRepository,
                              NfceRepository nfceRepository,
                              NfceSefazService sefaz,
                              NfceTransmissaoService transmissao,
                              FiscalProperties fiscal) {
        this.vendaRepository = vendaRepository;
        this.nfceRepository = nfceRepository;
        this.sefaz = sefaz;
        this.transmissao = transmissao;
        this.fiscal = fiscal;
    }

    @Transactional
    public Resultado emitir(Long vendaId) {
        if (fiscal.getCnpj() == null || fiscal.getCnpj().isBlank()) {
            return new Resultado(Status.NAO_CONFIGURADO, null,
                    "Antes de emitir, preencha os dados fiscais do emitente "
                    + "(CNPJ, inscrição estadual e endereço) nas configurações.");
        }

        var existente = nfceRepository.findByVendaId(vendaId);
        if (existente.isPresent() && existente.get().getStatus() == Nfce.Status.AUTORIZADO) {
            Nfce ja = existente.get();
            return new Resultado(Status.AUTORIZADA, ja.getChaveAcesso(),
                    "NFC-e já autorizada anteriormente (protocolo " + ja.getProtocolo() + ")");
        }

        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new IllegalArgumentException("Venda nº " + vendaId + " não encontrada"));
        if (venda.getCanceladaEm() != null) {
            throw new RegraNegocioException("Venda nº " + vendaId + " foi estornada — não emite NFC-e");
        }
        venda.getItens().size(); // força carregar os itens dentro da transação

        // numeração provisória: enquanto não há série própria controlada, usamos o
        // id da venda como nNF (garante unicidade; revisar quando houver numeração fiscal real)
        var montada = sefaz.montar(venda, vendaId.intValue());

        Certificado certificado;
        try {
            certificado = fiscal.temCertificadoArquivo()
                    ? CertificadoService.certificadoPfx(fiscal.getCertificadoCaminho(), fiscal.getCertificadoSenha())
                    : CertificadoService.getCertificadoByCnpjCpf(fiscal.getCnpj());
        } catch (CertificadoException | java.io.FileNotFoundException e) {
            return new Resultado(Status.PENDENTE_CERTIFICADO, montada.chaveAcesso(),
                    "NFC-e montada (chave " + montada.chaveAcesso() + "), mas não consegui carregar o "
                    + "certificado digital A1: " + e.getMessage());
        }

        NfceTransmissaoService.Resultado resultado;
        try {
            resultado = transmissao.transmitir(montada, certificado);
        } catch (Exception e) {
            salvar(venda, montada, Nfce.Status.ERRO, null, "Falha na transmissão: " + e.getMessage());
            return new Resultado(Status.ERRO_TRANSMISSAO, montada.chaveAcesso(),
                    "Falha ao transmitir a NFC-e à SEFAZ: " + e.getMessage());
        }

        String ambiente = fiscal.isHomologacao() ? " — AMBIENTE DE HOMOLOGAÇÃO, sem valor fiscal" : "";
        if (resultado.autorizada()) {
            salvar(venda, montada, Nfce.Status.AUTORIZADO, resultado.protocolo(), null);
            return new Resultado(Status.AUTORIZADA, montada.chaveAcesso(),
                    "NFC-e autorizada, protocolo " + resultado.protocolo() + ambiente);
        }
        String motivo = "[" + resultado.cStat() + "] " + resultado.xMotivo();
        salvar(venda, montada, Nfce.Status.ERRO, null, motivo);
        return new Resultado(Status.REJEITADA, montada.chaveAcesso(), "SEFAZ rejeitou a NFC-e: " + motivo + ambiente);
    }

    private void salvar(Venda venda, NfceSefazService.NfceMontada montada, Nfce.Status status,
                         String protocolo, String mensagem) {
        Nfce nfce = nfceRepository.findByVendaId(venda.getId()).orElseGet(Nfce::new);
        if (nfce.getVenda() == null) nfce.setVenda(venda);
        if (nfce.getRef() == null) nfce.setRef("venda-" + venda.getId());
        nfce.setStatus(status);
        nfce.setChaveAcesso(montada.chaveAcesso());
        nfce.setProtocolo(protocolo);
        nfce.setMensagem(mensagem);
        if (status == Nfce.Status.AUTORIZADO) nfce.setAutorizadaEm(Instant.now());
        nfceRepository.save(nfce);
    }
}
