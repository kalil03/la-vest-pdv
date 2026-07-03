package br.com.loja.pdv.service;

import br.com.loja.pdv.Fuso;
import br.com.loja.pdv.service.FechamentoMensalService.Fechamento;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Exportações do fechamento mensal — SEMPRE a partir do MESMO Fechamento que a
 * tela recebe (um cálculo, três saídas; nunca recalcular aqui).
 * CSV: BOM UTF-8 + ';' (o Excel pt-BR abre com acento e coluna certos).
 * PDF: PDFBox, A4, fontes padrão (WinAnsi) — evitar caracteres fora do WinAnsi.
 */
@Service
public class FechamentoExportService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DecimalFormat MOEDA =
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));

    private String mesAno(Fechamento f) {
        return Month.of(f.mes()).getDisplayName(TextStyle.FULL, PT_BR) + "/" + f.ano();
    }

    // ---------- CSV ----------

    public byte[] csv(Fechamento f) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Fechamento Mensal;").append(mesAno(f)).append('\n').append('\n');

        sb.append("VENDAS POR CATEGORIA;Qtd;Total\n");
        f.porCategoria().forEach(c ->
                sb.append(c.categoria()).append(';').append(c.qtd()).append(';')
                  .append(MOEDA.format(c.total())).append('\n'));
        sb.append("TOTAL GERAL;").append(f.qtdVendas()).append(';')
          .append(MOEDA.format(f.totalGeral())).append('\n').append('\n');

        sb.append("VENDAS POR VENDEDOR;Qtd;À vista;A prazo;Total\n");
        f.porVendedor().forEach(v ->
                sb.append(v.vendedor()).append(';').append(v.qtd()).append(';')
                  .append(MOEDA.format(v.aVista())).append(';')
                  .append(MOEDA.format(v.aPrazo())).append(';')
                  .append(MOEDA.format(v.total())).append('\n'));
        sb.append('\n');

        sb.append("OUTROS LANÇAMENTOS;Qtd;Total\n");
        sb.append("Recebimento de carnê (balcão);").append(f.recebimentoMes().qtd()).append(';')
          .append(MOEDA.format(f.recebimentoMes().total())).append('\n');
        sb.append("Entradas de fiado;").append(f.entradasFiado().qtd()).append(';')
          .append(MOEDA.format(f.entradasFiado().total())).append('\n');
        sb.append("Retiradas (sangria);").append(f.retiradas().qtd()).append(';')
          .append(MOEDA.format(f.retiradas().total())).append('\n');

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------- PDF ----------

    private static final float MARGEM = 50;
    private static final PDFont FONTE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont NEGRITO = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] pdf(Fechamento f, String nomeLoja) {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage(PDRectangle.A4);
            doc.addPage(pagina);
            float largura = pagina.getMediaBox().getWidth();
            float direita = largura - MARGEM;

            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                float y = pagina.getMediaBox().getHeight() - MARGEM;

                y = texto(cs, NEGRITO, 16, MARGEM, y, nomeLoja + " - Fechamento Mensal") - 4;
                y = texto(cs, FONTE, 12, MARGEM, y, mesAno(f)) - 2;
                y = texto(cs, FONTE, 8, MARGEM, y, "Gerado em " + LocalDateTime.now(Fuso.LOJA)
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))) - 14;

                y = titulo(cs, MARGEM, y, "VENDAS POR CATEGORIA");
                for (var c : f.porCategoria()) {
                    y = linhaValor(cs, y, direita, c.categoria() + "  (" + c.qtd() + " vendas)", c.total(), FONTE);
                }
                y = linhaValor(cs, y, direita, "TOTAL GERAL  (" + f.qtdVendas() + " vendas)", f.totalGeral(), NEGRITO) - 14;

                y = titulo(cs, MARGEM, y, "VENDAS POR VENDEDOR (à vista / a prazo)");
                for (var v : f.porVendedor()) {
                    y = linhaValor(cs, y, direita,
                            v.vendedor() + "  -  à vista R$ " + MOEDA.format(v.aVista())
                                    + "  /  a prazo R$ " + MOEDA.format(v.aPrazo()),
                            v.total(), FONTE);
                }
                y -= 14;

                y = titulo(cs, MARGEM, y, "OUTROS LANÇAMENTOS DO MÊS");
                y = linhaValor(cs, y, direita, "Recebimento de carnê no balcão  ("
                        + f.recebimentoMes().qtd() + ")", f.recebimentoMes().total(), FONTE);
                y = linhaValor(cs, y, direita, "Entradas de fiado  ("
                        + f.entradasFiado().qtd() + ")", f.entradasFiado().total(), FONTE);
                linhaValor(cs, y, direita, "Retiradas / sangria  ("
                        + f.retiradas().qtd() + ")", f.retiradas().total(), FONTE);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar o PDF do fechamento", e);
        }
    }

    private float texto(PDPageContentStream cs, PDFont fonte, float tam,
                        float x, float y, String s) throws IOException {
        cs.beginText();
        cs.setFont(fonte, tam);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
        return y - tam - 4;
    }

    private float titulo(PDPageContentStream cs, float x, float y, String s) throws IOException {
        return texto(cs, NEGRITO, 11, x, y, s) - 2;
    }

    /** Rótulo à esquerda, valor monetário alinhado à direita. */
    private float linhaValor(PDPageContentStream cs, float y, float direita,
                             String rotulo, BigDecimal valor, PDFont fonte) throws IOException {
        float tam = 10;
        String v = "R$ " + MOEDA.format(valor);
        texto(cs, fonte, tam, MARGEM, y, rotulo);
        float larguraValor = fonte.getStringWidth(v) / 1000 * tam;
        texto(cs, fonte, tam, direita - larguraValor, y, v);
        return y - tam - 5;
    }
}
