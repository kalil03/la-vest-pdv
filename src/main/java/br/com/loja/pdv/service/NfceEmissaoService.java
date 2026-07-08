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
 * do arquivo .pfx configurado (fiscal.certificado-caminho/senha — é o caminho
 * usado em produção); sem essa config, tenta o repositório de certificados do
 * Windows, que dispensa senha mas não funciona para ASSINAR nesta versão da
 * lib (KeyStore "Windows-MY" recusa getEntry com PasswordProtection).
 */
@Service
public class NfceEmissaoService {

    public enum Status {
        /** Dados do emitente não preenchidos: não dá nem para montar. */
        NAO_CONFIGURADO,
        /** XML montado, mas o certificado A1 não pôde ser carregado. */
        PENDENTE_CERTIFICADO,
        /** Autorizada pela SEFAZ. */
        AUTORIZADA,
        /** SEFAZ recebeu o lote e ainda está processando (cStat 103/105). */
        PROCESSANDO,
        /** SEFAZ recebeu e rejeitou (cStat de rejeição). */
        REJEITADA,
        /** Falha de rede/transmissão antes de obter resposta da SEFAZ. */
        ERRO_TRANSMISSAO
    }

    public record Resultado(Status status, String chaveAcesso, String mensagem) {}

    /** Posição do cNF dentro da chave de 44: cUF(2) AAMM(4) CNPJ(14) mod(2) serie(3) nNF(9) tpEmis(1) cNF(8) cDV(1). */
    private static final int CNF_INICIO = 35, CNF_FIM = 43;

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

        Nfce nfce = nfceRepository.findByVendaId(vendaId).orElse(null);
        if (nfce != null && nfce.getStatus() == Nfce.Status.AUTORIZADO) {
            return new Resultado(Status.AUTORIZADA, nfce.getChaveAcesso(),
                    "NFC-e já autorizada anteriormente (protocolo " + nfce.getProtocolo() + ")");
        }

        Venda venda = vendaRepository.findById(vendaId)
                .orElseThrow(() -> new IllegalArgumentException("Venda nº " + vendaId + " não encontrada"));
        if (venda.getCanceladaEm() != null) {
            throw new RegraNegocioException("Venda nº " + vendaId + " foi estornada — não emite NFC-e");
        }
        venda.getItens().size(); // força carregar os itens dentro da transação

        // numeração provisória: enquanto não há série própria controlada, usamos o
        // id da venda como nNF (garante unicidade; revisar quando houver numeração fiscal real)
        var montada = sefaz.montar(venda, vendaId.intValue(), cnfDaTentativaAnterior(nfce));

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
            salvar(nfce, venda, montada, Nfce.Status.ERRO, null, "Falha na transmissão: " + e.getMessage());
            return new Resultado(Status.ERRO_TRANSMISSAO, montada.chaveAcesso(),
                    "Falha ao transmitir a NFC-e à SEFAZ: " + e.getMessage());
        }

        String ambiente = fiscal.isHomologacao() ? " — AMBIENTE DE HOMOLOGAÇÃO, sem valor fiscal" : "";
        if (resultado.autorizada()) {
            salvar(nfce, venda, montada, Nfce.Status.AUTORIZADO, resultado.protocolo(), null);
            return new Resultado(Status.AUTORIZADA, montada.chaveAcesso(),
                    "NFC-e autorizada, protocolo " + resultado.protocolo() + ambiente);
        }
        String motivo = "[" + resultado.cStat() + "] " + resultado.xMotivo();
        if (resultado.emProcessamento()) {
            // ainda não é rejeição: a chave salva permite reconsultar sem duplicar
            salvar(nfce, venda, montada, Nfce.Status.PROCESSANDO, null, motivo);
            return new Resultado(Status.PROCESSANDO, montada.chaveAcesso(),
                    "SEFAZ recebeu o lote e ainda está processando (" + motivo + ") — tente de novo em instantes" + ambiente);
        }
        salvar(nfce, venda, montada, Nfce.Status.ERRO, null, motivo);
        return new Resultado(Status.REJEITADA, montada.chaveAcesso(), "SEFAZ rejeitou a NFC-e: " + motivo + ambiente);
    }

    /** cNF usado na tentativa anterior (persistido dentro da chave) — reusa no
     *  retry para reproduzir a mesma chave; senão a SEFAZ acusa duplicidade 539. */
    private static String cnfDaTentativaAnterior(Nfce nfce) {
        if (nfce == null || nfce.getChaveAcesso() == null || nfce.getChaveAcesso().length() != 44) return null;
        return nfce.getChaveAcesso().substring(CNF_INICIO, CNF_FIM);
    }

    private void salvar(Nfce nfce, Venda venda, NfceSefazService.NfceMontada montada, Nfce.Status status,
                         String protocolo, String mensagem) {
        if (nfce == null) nfce = new Nfce();
        nfce.setVenda(venda);
        nfce.setRef("venda-" + venda.getId());
        nfce.setStatus(status);
        nfce.setChaveAcesso(montada.chaveAcesso());
        nfce.setProtocolo(protocolo);
        nfce.setMensagem(mensagem);
        if (status == Nfce.Status.AUTORIZADO) nfce.setAutorizadaEm(Instant.now());
        nfceRepository.save(nfce);
    }
}
