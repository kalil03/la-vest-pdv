package br.com.loja.pdv;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Ajustes: config chave-valor (com fallback nos padrões) e gestão de operadores. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AjustesApiTest {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach // outros testes dependem do admin ativo (login) — restaura sempre
    void restaurarEstado() {
        jdbc.execute("TRUNCATE config");
        jdbc.execute("DELETE FROM usuario WHERE login <> 'admin'");
        jdbc.execute("UPDATE usuario SET ativo = true WHERE login = 'admin'");
    }

    private ResponseEntity<Map> put(Map<String, String> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("/api/config", HttpMethod.PUT, new HttpEntity<>(body, h), Map.class);
    }

    @Test
    void configSalvaSoOQueMudouEMantemPadraoDoResto() {
        var antes = http.getForEntity("/api/config", Map.class).getBody();
        assertThat(antes.get("carneVencDias")).isEqualTo("30"); // padrão

        assertThat(put(Map.of("nome", "Loja Teste", "carneVencDias", "45")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        var depois = http.getForEntity("/api/config", Map.class).getBody();
        assertThat(depois.get("nome")).isEqualTo("Loja Teste");
        assertThat(depois.get("carneVencDias")).isEqualTo("45");
        assertThat(depois.get("impLarguraMm")).isEqualTo("80"); // não mexeu: padrão intacto

        // valor inválido é recusado na porta
        assertThat(put(Map.of("carneVencDias", "abc")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(put(Map.of("impLarguraMm", "300")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void trocarSenhaExigeAAtualEOLoginPassaAUsarANova() {
        Long id = ((Number) http.postForEntity("/api/usuarios",
                Map.of("nome", "Rosana", "login", "rosana", "senha", "123"), Map.class)
                .getBody().get("id")).longValue();

        assertThat(http.postForEntity("/api/usuarios/" + id + "/senha",
                Map.of("senhaAtual", "errada", "senhaNova", "nova"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(http.postForEntity("/api/usuarios/" + id + "/senha",
                Map.of("senhaAtual", "123", "senhaNova", "nova"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(http.postForEntity("/api/login",
                Map.of("login", "rosana", "senha", "123"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST); // senha velha morreu
        assertThat(http.postForEntity("/api/login",
                Map.of("login", "rosana", "senha", "nova"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void desativarNuncaDeixaOSistemaSemOperador() {
        Long adminId = jdbc.queryForObject(
                "SELECT id FROM usuario WHERE login = 'admin'", Long.class);

        // admin é o único ativo: desativar é recusado
        assertThat(http.postForEntity("/api/usuarios/" + adminId + "/desativar", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // com um segundo operador, o admin pode sair — e o login dele morre
        http.postForEntity("/api/usuarios",
                Map.of("nome", "Rosana", "login", "rosana", "senha", "123"), Map.class);
        assertThat(http.postForEntity("/api/usuarios/" + adminId + "/desativar", null, Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.postForEntity("/api/login",
                Map.of("login", "admin", "senha", "admin"), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
