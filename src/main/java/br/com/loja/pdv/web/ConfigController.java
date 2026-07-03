package br.com.loja.pdv.web;

import br.com.loja.pdv.service.RegraNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ajustes da loja para as telas: cabeçalho da notinha, padrões do carnê e
 * impressão. Vivem na tabela config (chave-valor); enquanto uma chave não foi
 * salva pela tela de Ajustes, vale o padrão do application.properties — o
 * banco só guarda o que a loja mudou.
 */
@RestController
public class ConfigController {

    /** chave amigável da API → chave no banco */
    private static final Map<String, String> CHAVES = Map.of(
            "nome", "loja.nome",
            "endereco", "loja.endereco",
            "telefone", "loja.telefone",
            "carneVencDias", "carne.venc-dias",
            "carneParcelas", "carne.parcelas",
            "impLarguraMm", "impressao.largura-mm",
            "impRodape", "impressao.rodape");

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, String> padroes = new HashMap<>();

    public ConfigController(NamedParameterJdbcTemplate jdbc,
                            @Value("${loja.nome}") String nome,
                            @Value("${loja.endereco}") String endereco,
                            @Value("${loja.telefone}") String telefone) {
        this.jdbc = jdbc;
        padroes.put("nome", nome);
        padroes.put("endereco", endereco);
        padroes.put("telefone", telefone);
        padroes.put("carneVencDias", "30");
        padroes.put("carneParcelas", "1");
        padroes.put("impLarguraMm", "80");
        padroes.put("impRodape", "Obrigado pela preferência!");
    }

    @GetMapping("/api/config")
    public Map<String, String> config() {
        Map<String, String> banco = new HashMap<>();
        jdbc.query("SELECT chave, valor FROM config", rs -> {
            banco.put(rs.getString("chave"), rs.getString("valor"));
        });
        Map<String, String> out = new LinkedHashMap<>();
        CHAVES.forEach((amigavel, chaveBanco) ->
                out.put(amigavel, banco.getOrDefault(chaveBanco, padroes.get(amigavel))));
        return out;
    }

    @Transactional
    @PutMapping("/api/config")
    public Map<String, String> salvar(@RequestBody Map<String, String> body) {
        for (var e : body.entrySet()) {
            String chaveBanco = CHAVES.get(e.getKey());
            if (chaveBanco == null || e.getValue() == null) continue; // chave desconhecida: ignora
            validar(e.getKey(), e.getValue().trim());
            jdbc.update("""
                    INSERT INTO config (chave, valor) VALUES (:chave, :valor)
                    ON CONFLICT (chave) DO UPDATE SET valor = EXCLUDED.valor
                    """,
                    new MapSqlParameterSource()
                            .addValue("chave", chaveBanco).addValue("valor", e.getValue().trim()));
        }
        return config();
    }

    private void validar(String chave, String valor) {
        switch (chave) {
            case "nome" -> { if (valor.isBlank()) throw new RegraNegocioException("O nome da loja não pode ficar vazio"); }
            case "carneVencDias" -> exigirInteiro(valor, 1, 120, "Dias até a 1ª parcela deve ser entre 1 e 120");
            case "carneParcelas" -> exigirInteiro(valor, 1, 24, "Parcelas padrão deve ser entre 1 e 24");
            case "impLarguraMm" -> exigirInteiro(valor, 40, 90, "Largura da bobina deve ser entre 40 e 90 mm");
            default -> { }
        }
    }

    private void exigirInteiro(String valor, int min, int max, String mensagem) {
        try {
            int v = Integer.parseInt(valor);
            if (v < min || v > max) throw new RegraNegocioException(mensagem);
        } catch (NumberFormatException e) {
            throw new RegraNegocioException(mensagem);
        }
    }
}
