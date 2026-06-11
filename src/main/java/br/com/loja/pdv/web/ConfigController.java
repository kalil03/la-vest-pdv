package br.com.loja.pdv.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Dados da loja para o cabeçalho da notinha (configurados no application.properties). */
@RestController
public class ConfigController {

    private final Map<String, String> config;

    public ConfigController(@Value("${loja.nome}") String nome,
                            @Value("${loja.endereco}") String endereco,
                            @Value("${loja.telefone}") String telefone) {
        this.config = Map.of("nome", nome, "endereco", endereco, "telefone", telefone);
    }

    @GetMapping("/api/config")
    public Map<String, String> config() {
        return config;
    }
}
