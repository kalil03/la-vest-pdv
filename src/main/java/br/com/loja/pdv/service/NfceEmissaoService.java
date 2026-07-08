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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    public record Resultado(Status status, String chaveAcesso, String mensagem, Danfe danfe) {}

    /** Tudo que a DANFE NFC-e (cupom 80mm) precisa além dos itens/total da venda:
     *  identidade fiscal do emitente, chave, protocolo e o QR Code já em SVG.
     *  Preenchido só quando a nota está AUTORIZADA; caso contrário fica {@code null}. */
    public record Danfe(String razaoSocial, String cnpj, String inscricaoEstadual, String endereco,
                        String chave, String chaveFormatada, String protocolo, String dataAutorizacao,
                        String qrCodeSvg, String urlConsulta, boolean homologacao) {}

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
                    + "(CNPJ, inscrição estadual e endereço) nas configurações.", null);
        }

        Nfce nfce = nfceRepository.findByVendaId(vendaId).orElse(null);
        if (nfce != null && nfce.getStatus() == Nfce.Status.AUTORIZADO) {
            // já autorizada: regenera a DANFE (QR a partir da chave) para reimpressão
            return new Resultado(Status.AUTORIZADA, nfce.getChaveAcesso(),
                    "NFC-e já autorizada anteriormente (protocolo " + nfce.getProtocolo() + ")",
                    danfeSeguro(nfce.getChaveAcesso(), nfce.getProtocolo(), nfce.getAutorizadaEm(),
                            () -> sefaz.qrCodeDe(nfce.getChaveAcesso())));
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
                    + "certificado digital A1: " + e.getMessage(), null);
        }

        NfceTransmissaoService.Resultado resultado;
        try {
            resultado = transmissao.transmitir(montada, certificado);
        } catch (Exception e) {
            salvar(nfce, venda, montada, Nfce.Status.ERRO, null, "Falha na transmissão: " + e.getMessage(), null);
            return new Resultado(Status.ERRO_TRANSMISSAO, montada.chaveAcesso(),
                    "Falha ao transmitir a NFC-e à SEFAZ: " + e.getMessage(), null);
        }

        String ambiente = fiscal.isHomologacao() ? " — AMBIENTE DE HOMOLOGAÇÃO, sem valor fiscal" : "";
        if (resultado.autorizada()) {
            salvar(nfce, venda, montada, Nfce.Status.AUTORIZADO, resultado.protocolo(), null, resultado.xml());
            return new Resultado(Status.AUTORIZADA, montada.chaveAcesso(),
                    "NFC-e autorizada, protocolo " + resultado.protocolo() + ambiente,
                    danfeSeguro(montada.chaveAcesso(), resultado.protocolo(), Instant.now(), montada::qrCode));
        }
        String motivo = "[" + resultado.cStat() + "] " + resultado.xMotivo();
        if (resultado.emProcessamento()) {
            // ainda não é rejeição: a chave salva permite reconsultar sem duplicar
            salvar(nfce, venda, montada, Nfce.Status.PROCESSANDO, null, motivo, null);
            return new Resultado(Status.PROCESSANDO, montada.chaveAcesso(),
                    "SEFAZ recebeu o lote e ainda está processando (" + motivo + ") — tente de novo em instantes" + ambiente, null);
        }
        salvar(nfce, venda, montada, Nfce.Status.ERRO, null, motivo, null);
        return new Resultado(Status.REJEITADA, montada.chaveAcesso(), "SEFAZ rejeitou a NFC-e: " + motivo + ambiente, null);
    }

    /** Monta a DANFE para impressão; se o QR falhar (ex.: CSC ausente), devolve a
     *  DANFE sem o SVG em vez de derrubar o resultado da autorização. */
    private Danfe danfeSeguro(String chave, String protocolo, Instant autorizadaEm,
                              java.util.function.Supplier<String> qrConteudo) {
        String svg = null;
        try {
            svg = QrCodeSvgUtil.svg(qrConteudo.get());
        } catch (RuntimeException e) {
            // sem QR a DANFE ainda imprime chave + protocolo (consulta manual no site)
        }
        String data = LocalDateTime.ofInstant(
                autorizadaEm != null ? autorizadaEm : Instant.now(), br.com.loja.pdv.Fuso.LOJA).format(DATA_HORA);
        return new Danfe(fiscal.getRazaoSocial(), fiscal.getCnpj(), fiscal.getInscricaoEstadual(),
                enderecoLinha(), chave, formatarChave(chave), protocolo, data,
                svg, fiscal.getNfce().getUrlQrcode(), fiscal.isHomologacao());
    }

    private String enderecoLinha() {
        var e = fiscal.getEndereco();
        StringBuilder sb = new StringBuilder();
        if (e.getLogradouro() != null) sb.append(e.getLogradouro());
        if (e.getNumero() != null && !e.getNumero().isBlank()) sb.append(", ").append(e.getNumero());
        if (e.getBairro() != null && !e.getBairro().isBlank()) sb.append(" - ").append(e.getBairro());
        if (e.getMunicipio() != null && !e.getMunicipio().isBlank()) sb.append(" - ").append(e.getMunicipio());
        if (fiscal.getUf() != null) sb.append('/').append(fiscal.getUf());
        return sb.toString();
    }

    /** Chave de 44 dígitos em blocos de 4, como sai na DANFE oficial. */
    private static String formatarChave(String chave) {
        if (chave == null) return "";
        return chave.replaceAll("(.{4})", "$1 ").trim();
    }

    /** cNF usado na tentativa anterior (persistido dentro da chave) — reusa no
     *  retry para reproduzir a mesma chave; senão a SEFAZ acusa duplicidade 539. */
    private static String cnfDaTentativaAnterior(Nfce nfce) {
        if (nfce == null || nfce.getChaveAcesso() == null || nfce.getChaveAcesso().length() != 44) return null;
        return nfce.getChaveAcesso().substring(CNF_INICIO, CNF_FIM);
    }

    private void salvar(Nfce nfce, Venda venda, NfceSefazService.NfceMontada montada, Nfce.Status status,
                         String protocolo, String mensagem, String xml) {
        if (nfce == null) nfce = new Nfce();
        nfce.setVenda(venda);
        nfce.setRef("venda-" + venda.getId());
        nfce.setStatus(status);
        nfce.setChaveAcesso(montada.chaveAcesso());
        nfce.setProtocolo(protocolo);
        nfce.setMensagem(mensagem);
        if (xml != null) nfce.setXml(xml);
        if (status == Nfce.Status.AUTORIZADO) nfce.setAutorizadaEm(Instant.now());
        nfceRepository.save(nfce);
    }
}
