package br.com.loja.pdv;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import br.com.swconsultoria.nfe.Nfe;
import br.com.swconsultoria.nfe.dom.ConfiguracoesNfe;
import br.com.swconsultoria.nfe.dom.enuns.AmbienteEnum;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.dom.enuns.PessoaEnum;
import br.com.swconsultoria.nfe.schema.consCad.TRetConsCad;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Diagnóstico MANUAL (não roda no build): pergunta ao webservice oficial
 * ConsultaCadastro da SEFAZ-PR a situação cadastral do emitente — se a IE está
 * habilitada e se o CNPJ consta como credenciado a emitir NF-e/NFC-e.
 * Usado para investigar a rejeição [781]. Rodar com:
 *   mvnw -Dtest=ConsultaCadastroSefazManualTest -Dsurefire.failIfNoSpecifiedTests=false test -Dgroups=
 * (remover @Disabled antes)
 */
class ConsultaCadastroSefazManualTest {

    @Test
    @Disabled("diagnóstico manual — consulta a SEFAZ de verdade; remover a anotação para rodar")
    void consultaCadastroEmitente() throws Exception {
        Certificado cert = CertificadoService.certificadoPfx(
                "C:\\Set Sistemas\\Certificado Digital\\C PEREIRA PINTOVESTUARIO.pfx", "lider123");
        ConfiguracoesNfe config = ConfiguracoesNfe.criarConfiguracoes(
                EstadosEnum.PR, AmbienteEnum.PRODUCAO, cert, "C:\\LaVest\\schemas");

        TRetConsCad ret = Nfe.consultaCadastro(config, PessoaEnum.JURIDICA, "08935571000120", EstadosEnum.PR);

        var inf = ret.getInfCons();
        System.out.println("==== ConsultaCadastro SEFAZ-PR (producao) ====");
        System.out.println("cStat  : " + inf.getCStat());
        System.out.println("xMotivo: " + inf.getXMotivo());
        for (var cad : inf.getInfCad()) {
            System.out.println("---- inscricao ----");
            System.out.println("IE          : " + cad.getIE());
            System.out.println("CNPJ        : " + cad.getCNPJ());
            System.out.println("razao       : " + cad.getXNome());
            System.out.println("cSit (1=hab): " + cad.getCSit());
            System.out.println("indCredNFe  : " + cad.getIndCredNFe());
            System.out.println("indCredCTe  : " + cad.getIndCredCTe());
            System.out.println("regApur     : " + cad.getXRegApur());
            System.out.println("dtIniAtiv   : " + cad.getDIniAtiv());
            System.out.println("dtUltSit    : " + cad.getDUltSit());
        }
    }
}
