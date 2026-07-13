package br.com.loja.pdv;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.ConsultaDFeEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.PessoaEnum;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Diagnóstico MANUAL (não roda no build): baixa da SEFAZ (DistribuicaoDFe por
 * chave) o XML autorizado das NFC-e cujo XML não ficou gravado localmente
 * (emitidas antes da V23). Salva na pasta de XMLs da Área de Trabalho.
 */
class BaixarXmlSefazManualTest {

    private static final String[] CHAVES = {
            "41260708935571000120650020000000071223036928", // venda 7
            "41260708935571000120650020000000121795884134", // venda 12
            "41260708935571000120650020000000181843376575", // venda 18
    };

    @Test
    @Disabled("diagnóstico manual — consulta a SEFAZ de verdade; remover a anotação para rodar")
    void baixarXmlsAutorizados() throws Exception {
        Certificado cert = CertificadoService.certificadoPfx(
                "C:\\Set Sistemas\\Certificado Digital\\C PEREIRA PINTOVESTUARIO.pfx", "lider123");
        ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(
                EstadosEnum.PR, AmbienteEnum.PRODUCAO, cert, "C:\\LaVest\\schemas");

        Path dir = Path.of("C:\\Users\\PC\\Desktop\\XMLs NFC-e");
        Files.createDirectories(dir);

        for (String chave : CHAVES) {
            System.out.println("==== chave " + chave + " ====");
            var ret = Nfe.distribuicaoDfe(config, PessoaEnum.JURIDICA, "08935571000120",
                    ConsultaDFeEnum.CHAVE, chave);
            System.out.println("cStat=" + ret.getCStat() + " xMotivo=" + ret.getXMotivo());
            if (ret.getLoteDistDFeInt() == null) continue;
            for (var doc : ret.getLoteDistDFeInt().getDocZip()) {
                byte[] xml = new GZIPInputStream(new ByteArrayInputStream(doc.getValue())).readAllBytes();
                String schema = doc.getSchema();
                System.out.println("  doc schema=" + schema + " bytes=" + xml.length);
                if (schema != null && schema.startsWith("procNFe")) {
                    String vendaNum = String.valueOf(Long.parseLong(chave.substring(25, 34)));
                    Path destino = dir.resolve("venda-" + vendaNum + ".xml");
                    Files.write(destino, xml);
                    System.out.println("  SALVO: " + destino);
                }
            }
        }
    }
}
