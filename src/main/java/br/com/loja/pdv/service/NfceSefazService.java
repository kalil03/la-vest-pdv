package br.com.loja.pdv.service;

import br.com.loja.pdv.domain.*;
import br.com.swconsultoria.nfe.dom.enuns.EstadosEnum;
import br.com.swconsultoria.nfe.schema_4.enviNFe.*;
import br.com.swconsultoria.nfe.util.ChaveUtil;
import br.com.swconsultoria.nfe.util.ConstantesUtil;
import br.com.swconsultoria.nfe.util.NFCeUtil;
import br.com.swconsultoria.nfe.util.XmlNfeUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Monta a NFC-e (modelo 65) no layout 4.00 da SEFAZ a partir de uma {@link Venda},
 * usando o modelo JAXB da biblioteca swconsultoria/nfe — caminho GRATUITO, sem
 * gateway pago. Esta classe vai até o XML NÃO assinado + chave de acesso + QR Code.
 *
 * <p>A assinatura digital e a transmissão SOAP à SEFAZ entram na fase do certificado
 * (precisam do A1 .pfx da loja). Aqui não há I/O nem rede: é tradução pura, testável.
 *
 * <p>Tributação: o produto pode trazer CSOSN/CFOP próprios; sem isso, vale o padrão
 * da loja (Simples Nacional, CSOSN 102 = sem permissão de crédito). O desconto da
 * venda é rateado entre os itens para o total da nota fechar com o que foi cobrado.
 */
@Service
public class NfceSefazService {

    private static final String UF_PADRAO = "1"; // tpEmis 1 = emissão normal
    private static final ZoneId FUSO = br.com.loja.pdv.Fuso.LOJA;
    private static final String AVISO_HOMOLOGACAO =
            "NOTA FISCAL EMITIDA EM AMBIENTE DE HOMOLOGACAO - SEM VALOR FISCAL";

    private final FiscalProperties fiscal;
    private final ObjectFactory of = new ObjectFactory();

    public NfceSefazService(FiscalProperties fiscal) {
        this.fiscal = fiscal;
    }

    /** Resultado da montagem: o objeto pronto para assinar (nfe), a chave, o XML (prévia, não
     *  assinado) e o conteúdo do QR Code. */
    public record NfceMontada(String chaveAcesso, TNFe nfe, String xml, String qrCode) {}

    /**
     * Monta o XML da NFC-e para a venda.
     *
     * @param venda  a venda já fechada (com itens e total)
     * @param numero número sequencial da NFC-e (nNF) — controlado pelo emitente
     */
    public NfceMontada montar(Venda venda, int numero) {
        EstadosEnum estado = EstadosEnum.valueOf(fiscal.getUf());
        LocalDateTime agora = LocalDateTime.ofInstant(
                venda.getData() != null ? venda.getData() : java.time.Instant.now(), FUSO);
        String cNF = gerarCodigoNumerico(numero);

        ChaveUtil chaveUtil = new ChaveUtil(estado, fiscal.getCnpj(), "65",
                fiscal.getNfce().getSerie(), numero, UF_PADRAO, cNF, agora);
        String chave = chaveUtil.getChaveNF().replace("NFe", "");

        TNFe.InfNFe inf = new TNFe.InfNFe();
        inf.setVersao(ConstantesUtil.VERSAO.NFE);
        inf.setId("NFe" + chave);
        inf.setIde(montarIde(estado, numero, cNF, chaveUtil.getDigitoVerificador(), agora));
        inf.setEmit(montarEmit(estado));
        if (venda.getCliente() != null && temCpf(venda.getCliente())) {
            inf.setDest(montarDest(venda.getCliente()));
        }
        BigDecimal totalProdutos = adicionarItens(venda, inf);
        inf.setTotal(montarTotal(totalProdutos, descontoDe(venda)));
        inf.setTransp(montarTransp());
        inf.setPag(montarPag(venda));
        inf.setInfRespTec(montarRespTecnico());

        TNFe nfe = new TNFe();
        nfe.setInfNFe(inf);

        String qrCode = gerarQrCode(chave, fiscal.tpAmb());
        TNFe.InfNFeSupl supl = new TNFe.InfNFeSupl();
        supl.setQrCode(qrCode);
        supl.setUrlChave(fiscal.getNfce().getUrlQrcode());
        nfe.setInfNFeSupl(supl);

        return new NfceMontada(chave, nfe, marshal(nfe), qrCode);
    }

    /** QR Code v2 (chave + hash SHA-1 de chave+CSC) — o que o app do consumidor valida. */
    private String gerarQrCode(String chave, String tpAmb) {
        try {
            return NFCeUtil.getCodeQRCode(chave, tpAmb, fiscal.getCscId(), fiscal.getCsc(),
                    fiscal.getNfce().getUrlQrcode());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao gerar QR Code da NFC-e: " + e.getMessage(), e);
        }
    }

    private TNFe.InfNFe.Ide montarIde(EstadosEnum estado, int numero, String cNF,
                                      String cDV, LocalDateTime agora) {
        TNFe.InfNFe.Ide ide = new TNFe.InfNFe.Ide();
        ide.setCUF(estado.getCodigoUF());
        ide.setCNF(cNF);
        ide.setNatOp("Venda ao consumidor");
        ide.setMod("65");                       // NFC-e
        ide.setSerie(String.valueOf(fiscal.getNfce().getSerie()));
        ide.setNNF(String.valueOf(numero));
        ide.setDhEmi(XmlNfeUtil.dataNfe(agora, FUSO));
        ide.setTpNF("1");                        // saída
        ide.setIdDest("1");                      // operação interna
        ide.setCMunFG(fiscal.getEndereco().getCodMunicipio());
        ide.setTpImp("4");                       // DANFE NFC-e
        ide.setTpEmis(UF_PADRAO);                // 1 = normal
        ide.setCDV(cDV);
        ide.setTpAmb(fiscal.tpAmb());
        ide.setFinNFe("1");                      // normal
        ide.setIndFinal("1");                    // consumidor final
        ide.setIndPres("1");                     // presencial
        ide.setProcEmi("0");                     // aplicativo do contribuinte
        ide.setVerProc("la-vest-pdv");
        return ide;
    }

    /** Ordem dos campos segue o XSD (leiauteNFe_v4.00): CNPJ, xNome, xFant,
     *  enderEmit, IE, [IEST], [IM, CNAE], CRT — a serialização é sensível a isso. */
    private TNFe.InfNFe.Emit montarEmit(EstadosEnum estado) {
        TNFe.InfNFe.Emit emit = new TNFe.InfNFe.Emit();
        emit.setCNPJ(fiscal.getCnpj());
        emit.setXNome(fiscal.getRazaoSocial());
        if (naoVazio(fiscal.getNomeFantasia())) emit.setXFant(fiscal.getNomeFantasia());

        FiscalProperties.Endereco e = fiscal.getEndereco();
        TEnderEmi end = new TEnderEmi();
        end.setXLgr(e.getLogradouro());
        end.setNro(e.getNumero());
        end.setXBairro(e.getBairro());
        end.setCMun(e.getCodMunicipio());
        end.setXMun(e.getMunicipio());
        end.setUF(TUfEmi.valueOf(fiscal.getUf()));
        end.setCEP(e.getCep());
        end.setCPais("1058");
        end.setXPais("BRASIL");
        if (naoVazio(e.getFone())) end.setFone(e.getFone());
        emit.setEnderEmit(end);

        emit.setIE(fiscal.getInscricaoEstadual());
        // CNAE só pode aparecer junto de IM (grupo "interesse da Prefeitura", p/ prestador de
        // serviço) — a loja não tem IM (é comércio, não serviço), então não manda nenhum dos dois.
        emit.setCRT(fiscal.getCrt());
        return emit;
    }

    private TNFe.InfNFe.Dest montarDest(Cliente cliente) {
        TNFe.InfNFe.Dest dest = new TNFe.InfNFe.Dest();
        dest.setCPF(soDigitos(cliente.getCpf()));
        // Em homologação a razão social do destinatário deve ser o aviso padrão.
        dest.setXNome(fiscal.isHomologacao() ? AVISO_HOMOLOGACAO : cliente.getNome());
        dest.setIndIEDest("9");                  // não contribuinte
        return dest;
    }

    /** Cria os itens (det), retornando a soma dos valores brutos dos produtos. */
    private BigDecimal adicionarItens(Venda venda, TNFe.InfNFe inf) {
        BigDecimal totalProdutos = BigDecimal.ZERO;
        int numero = 1;
        for (ItemVenda item : venda.getItens()) {
            TNFe.InfNFe.Det det = new TNFe.InfNFe.Det();
            det.setNItem(String.valueOf(numero));
            det.setProd(montarProd(item, numero == 1));
            det.setImposto(montarImposto(item));
            inf.getDet().add(det);
            totalProdutos = totalProdutos.add(item.getSubtotal());
            numero++;
        }
        return totalProdutos;
    }

    private TNFe.InfNFe.Det.Prod montarProd(ItemVenda item, boolean primeiro) {
        Produto p = item.getVariacao().getProduto();
        TNFe.InfNFe.Det.Prod prod = new TNFe.InfNFe.Det.Prod();
        prod.setCProd(p.getCodigo());
        prod.setCEAN("SEM GTIN");
        // Em homologação o xProd do primeiro item carrega o aviso "sem valor fiscal".
        prod.setXProd(fiscal.isHomologacao() && primeiro ? AVISO_HOMOLOGACAO : descricao(item.getVariacao()));
        prod.setNCM(p.getNcm());
        prod.setCFOP(valorOuPadrao(p.getCfop(), fiscal.getCfopPadrao()));
        String unidade = naoVazio(p.getUnidade()) ? p.getUnidade() : "UN";
        prod.setUCom(unidade);
        prod.setQCom(quantidade(item.getQuantidade()));
        prod.setVUnCom(moeda(item.getPrecoUnit()));
        prod.setVProd(moeda(item.getSubtotal()));
        prod.setCEANTrib("SEM GTIN");
        prod.setUTrib(unidade);
        prod.setQTrib(quantidade(item.getQuantidade()));
        prod.setVUnTrib(moeda(item.getPrecoUnit()));
        prod.setIndTot("1");                      // compõe o total da nota
        return prod;
    }

    /** ICMS (CSOSN — Simples), PIS e COFINS sem tributo (CST 07/49 — confirmar c/ contador). */
    private TNFe.InfNFe.Det.Imposto montarImposto(ItemVenda item) {
        Produto p = item.getVariacao().getProduto();

        TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102 sn = new TNFe.InfNFe.Det.Imposto.ICMS.ICMSSN102();
        sn.setOrig(String.valueOf(p.getOrigem() != null ? p.getOrigem() : 0));
        sn.setCSOSN(valorOuPadrao(p.getCsosn(), fiscal.getCsosnPadrao()));
        TNFe.InfNFe.Det.Imposto.ICMS icms = new TNFe.InfNFe.Det.Imposto.ICMS();
        icms.setICMSSN102(sn);

        TNFe.InfNFe.Det.Imposto.PIS.PISNT pisNt = new TNFe.InfNFe.Det.Imposto.PIS.PISNT();
        pisNt.setCST("07");
        TNFe.InfNFe.Det.Imposto.PIS pis = new TNFe.InfNFe.Det.Imposto.PIS();
        pis.setPISNT(pisNt);

        TNFe.InfNFe.Det.Imposto.COFINS.COFINSNT cofinsNt = new TNFe.InfNFe.Det.Imposto.COFINS.COFINSNT();
        cofinsNt.setCST("07");
        TNFe.InfNFe.Det.Imposto.COFINS cofins = new TNFe.InfNFe.Det.Imposto.COFINS();
        cofins.setCOFINSNT(cofinsNt);

        TNFe.InfNFe.Det.Imposto imposto = new TNFe.InfNFe.Det.Imposto();
        imposto.getContent().add(of.createTNFeInfNFeDetImpostoICMS(icms));
        imposto.getContent().add(of.createTNFeInfNFeDetImpostoPIS(pis));
        imposto.getContent().add(of.createTNFeInfNFeDetImpostoCOFINS(cofins));
        return imposto;
    }

    private TNFe.InfNFe.Total montarTotal(BigDecimal totalProdutos, BigDecimal desconto) {
        TNFe.InfNFe.Total.ICMSTot tot = new TNFe.InfNFe.Total.ICMSTot();
        String zero = "0.00";
        tot.setVBC(zero);
        tot.setVICMS(zero);
        tot.setVICMSDeson(zero);
        tot.setVFCP(zero);
        tot.setVBCST(zero);
        tot.setVST(zero);
        tot.setVFCPST(zero);
        tot.setVFCPSTRet(zero);
        tot.setVProd(moeda(totalProdutos));
        tot.setVFrete(zero);
        tot.setVSeg(zero);
        tot.setVDesc(moeda(desconto));
        tot.setVII(zero);
        tot.setVIPI(zero);
        tot.setVIPIDevol(zero);
        tot.setVPIS(zero);
        tot.setVCOFINS(zero);
        tot.setVOutro(zero);
        tot.setVNF(moeda(totalProdutos.subtract(desconto)));
        TNFe.InfNFe.Total total = new TNFe.InfNFe.Total();
        total.setICMSTot(tot);
        return total;
    }

    /** Exigido pela SEFAZ desde a NT2020.001 — identifica quem desenvolve/mantém o sistema. */
    private TInfRespTec montarRespTecnico() {
        FiscalProperties.RespTecnico rt = fiscal.getRespTecnico();
        TInfRespTec infRespTec = new TInfRespTec();
        infRespTec.setCNPJ(rt.getCnpj());
        infRespTec.setXContato(rt.getContato());
        infRespTec.setEmail(rt.getEmail());
        infRespTec.setFone(rt.getFone());
        return infRespTec;
    }

    /** NFC-e é sempre sem frete por conta do emitente. */
    private TNFe.InfNFe.Transp montarTransp() {
        TNFe.InfNFe.Transp transp = new TNFe.InfNFe.Transp();
        transp.setModFrete("9");                  // sem ocorrência de transporte
        return transp;
    }

    private TNFe.InfNFe.Pag montarPag(Venda venda) {
        TNFe.InfNFe.Pag.DetPag detPag = new TNFe.InfNFe.Pag.DetPag();
        detPag.setTPag(formaPagamentoSefaz(venda.getFormaPagamento()));
        detPag.setVPag(moeda(venda.getTotal()));
        TNFe.InfNFe.Pag pag = new TNFe.InfNFe.Pag();
        pag.getDetPag().add(detPag);
        return pag;
    }

    /** SEFAZ: tag tPag a partir da nossa forma de pagamento. */
    private static String formaPagamentoSefaz(FormaPagamento f) {
        return switch (f) {
            case DINHEIRO -> "01";
            case PIX -> "17";
            case CARTAO -> "99";                  // genérico; sem distinguir crédito/débito
            case FIADO -> "05";                   // crédito loja (crediário próprio)
        };
    }

    private String marshal(TNFe nfe) {
        try {
            return XmlNfeUtil.objectToXml(nfe);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o XML da NFC-e: " + e.getMessage(), e);
        }
    }

    /** cNF: 8 dígitos pseudoaleatórios; não pode ser igual ao nNF (regra da SEFAZ). */
    private static String gerarCodigoNumerico(int numero) {
        int codigo;
        do {
            codigo = ThreadLocalRandom.current().nextInt(0, 100_000_000);
        } while (codigo == numero);
        return String.format("%08d", codigo);
    }

    private static String descricao(Variacao v) {
        StringJoiner sj = new StringJoiner(" ");
        sj.add(v.getProduto().getNome());
        if (naoVazio(v.getTamanho())) sj.add(v.getTamanho());
        if (naoVazio(v.getCor())) sj.add(v.getCor());
        return sj.toString();
    }

    private static BigDecimal descontoDe(Venda venda) {
        return venda.getDesconto() != null ? venda.getDesconto() : BigDecimal.ZERO;
    }

    private static boolean temCpf(Cliente c) {
        return naoVazio(c.getCpf()) && !soDigitos(c.getCpf()).isEmpty();
    }

    private static String soDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String moeda(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String quantidade(int qtd) {
        return BigDecimal.valueOf(qtd).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String valorOuPadrao(String valor, String padrao) {
        return naoVazio(valor) ? valor : padrao;
    }

    private static boolean naoVazio(String s) {
        return s != null && !s.isBlank();
    }
}
