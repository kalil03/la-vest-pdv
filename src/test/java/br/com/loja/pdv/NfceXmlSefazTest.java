package br.com.loja.pdv;

import br.com.loja.pdv.domain.*;
import br.com.loja.pdv.service.FiscalProperties;
import br.com.loja.pdv.service.NfceSefazService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Montagem do XML da NFC-e direto na SEFAZ (caminho gratuito) — teste unitário
 * puro: sem banco, sem certificado, sem rede. Valida a tradução Venda -> XML 4.00.
 */
class NfceXmlSefazTest {

    private FiscalProperties fiscal() {
        FiscalProperties f = new FiscalProperties();
        f.setAmbiente("homologacao");
        f.setUf("PR");
        f.setCnpj("12345678000199");
        f.setInscricaoEstadual("9012345678");
        f.setRazaoSocial("LOJA LA VEST LTDA");
        f.setNomeFantasia("La Vest");
        f.getEndereco().setLogradouro("RUA DAS FLORES");
        f.getEndereco().setNumero("100");
        f.getEndereco().setBairro("CENTRO");
        f.getEndereco().setMunicipio("CURITIBA");
        f.getEndereco().setCodMunicipio("4106902");
        f.getEndereco().setCep("80000000");
        f.getNfce().setSerie(1);
        f.getNfce().setUrlQrcode("http://www.fazenda.pr.gov.br/nfce/qrcode");
        // QR v2 leva hash de chave+CSC — sem CSC a montagem recusa (guarda explícita)
        f.setCsc("CSC-DE-TESTE-NAO-E-REAL-123456");
        f.setCscId("000001");
        return f;
    }

    private Produto produto(String codigo, String nome, String ncm, String csosn) {
        Produto p = new Produto();
        p.setCodigo(codigo);
        p.setNome(nome);
        p.setNcm(ncm);
        p.setUnidade("UN");
        p.setOrigem(0);
        p.setCsosn(csosn);
        Variacao v = new Variacao();
        p.adicionarVariacao(v);
        return p;
    }

    private ItemVenda item(Produto p, int qtd, String preco) {
        ItemVenda i = new ItemVenda();
        i.setVariacao(p.getVariacoes().get(0));
        i.setQuantidade(qtd);
        i.setPrecoUnit(new BigDecimal(preco));
        return i;
    }

    @Test
    void vendaAVistaViraXmlNfceComChaveItemEPagamento() {
        NfceSefazService service = new NfceSefazService(fiscal());
        Produto tenis = produto("T100", "Tenis Runner", "64041900", null);
        Venda venda = new Venda();
        venda.setData(Instant.parse("2026-06-18T15:00:00Z"));
        venda.setFormaPagamento(FormaPagamento.DINHEIRO);
        venda.adicionarItem(item(tenis, 2, "150.00"));
        venda.setDesconto(BigDecimal.ZERO);
        venda.setTotal(new BigDecimal("300.00"));

        var nfce = service.montar(venda, 1);

        // chave: 44 dígitos, UF 41 (PR), modelo 65
        assertThat(nfce.chaveAcesso()).hasSize(44).startsWith("41");
        assertThat(nfce.chaveAcesso().substring(20, 22)).isEqualTo("65");
        // QR v2: chave|2|tpAmb|idCsc|hash — o formato que o validador da SEFAZ-PR aceita
        assertThat(nfce.qrCode()).contains("?p=" + nfce.chaveAcesso() + "|2|2|1|");

        String xml = nfce.xml();
        assertThat(xml).contains("<mod>65</mod>");
        assertThat(xml).contains("<tpAmb>2</tpAmb>");
        assertThat(xml).contains("<CNPJ>12345678000199</CNPJ>");
        assertThat(xml).contains("<NCM>64041900</NCM>");
        assertThat(xml).contains("<CFOP>5102</CFOP>");          // padrão da loja
        assertThat(xml).contains("<CSOSN>102</CSOSN>");          // CSOSN padrão
        assertThat(xml).contains("<vProd>300.00</vProd>");
        assertThat(xml).contains("<vNF>300.00</vNF>");
        assertThat(xml).contains("<tPag>01</tPag>");             // dinheiro
        assertThat(xml).contains("<vPag>300.00</vPag>");
        // homologação: 1º item carrega o aviso "sem valor fiscal"
        assertThat(xml).contains("SEM VALOR FISCAL");
    }

    @Test
    void cpfDoClienteEntraComoDestinatario() {
        NfceSefazService service = new NfceSefazService(fiscal());
        Produto p = produto("A", "Camiseta", "61091000", null);
        Cliente cliente = new Cliente();
        cliente.setNome("Maria Souza");
        cliente.setCpf("111.444.777-35");
        Venda venda = new Venda();
        venda.setData(Instant.parse("2026-06-18T15:00:00Z"));
        venda.setCliente(cliente);
        venda.setFormaPagamento(FormaPagamento.PIX);
        venda.adicionarItem(item(p, 1, "80.00"));
        venda.setTotal(new BigDecimal("80.00"));

        var nfce = service.montar(venda, 2);

        assertThat(nfce.xml()).contains("<CPF>11144477735</CPF>"); // só dígitos
        assertThat(nfce.xml()).contains("<tPag>17</tPag>");        // pix
    }
}
