package br.com.loja.pdv.service;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import java.util.EnumMap;
import java.util.Map;

/**
 * Gera o QR Code da NFC-e como SVG — 100% offline (ZXing local, sem CDN nem rede).
 * SVG vetorial imprime nítido em qualquer resolução da térmica, ao contrário de um
 * PNG rasterizado. Cada módulo vira um retângulo de 1×1 num único &lt;path&gt;, com
 * zona de silêncio (quiet zone) de 4 módulos exigida pela especificação do QR.
 */
public final class QrCodeSvgUtil {

    private QrCodeSvgUtil() {}

    /** Zona de silêncio mínima do padrão QR (4 módulos de cada lado). */
    private static final int QUIET = 4;

    public static String svg(String conteudo) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            // nível M: equilíbrio recomendado para NFC-e (recupera ~15% de dano)
            QRCode qr = Encoder.encode(conteudo, ErrorCorrectionLevel.M, hints);
            ByteMatrix m = qr.getMatrix();
            int n = m.getWidth();
            int dim = n + QUIET * 2;

            StringBuilder path = new StringBuilder();
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (m.get(x, y) == 1) {
                        path.append('M').append(x + QUIET).append(' ').append(y + QUIET).append("h1v1h-1z");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + dim + ' ' + dim
                    + "\" shape-rendering=\"crispEdges\" width=\"100%\" height=\"100%\">"
                    + "<rect width=\"" + dim + "\" height=\"" + dim + "\" fill=\"#fff\"/>"
                    + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
        } catch (WriterException e) {
            throw new IllegalStateException("Falha ao gerar SVG do QR Code: " + e.getMessage(), e);
        }
    }
}
