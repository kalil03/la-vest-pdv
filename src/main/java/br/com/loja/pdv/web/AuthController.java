package br.com.loja.pdv.web;

import br.com.loja.pdv.domain.Usuario;
import br.com.loja.pdv.repository.UsuarioRepository;
import br.com.loja.pdv.service.RegraNegocioException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Login simples por funcionário: valida credencial e devolve o usuário;
 * o frontend guarda em localStorage e cada tela exige isso para abrir.
 * Sem sessão no servidor — é controle de balcão, não segurança de banco.
 */
@RestController
public class AuthController {

    private final UsuarioRepository usuarioRepository;

    public AuthController(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String senha) {}
    public record NovoUsuario(@NotBlank(message = "Login é obrigatório") String login,
                              @NotBlank(message = "Nome é obrigatório") String nome,
                              @NotBlank(message = "Senha é obrigatória") String senha) {}

    @PostMapping("/api/login")
    public Map<String, Object> login(@RequestBody @jakarta.validation.Valid LoginRequest req) {
        Usuario u = usuarioRepository.findByLoginIgnoreCaseAndAtivoTrue(req.login().trim())
                .filter(usuario -> hash(usuario.getSal(), req.senha()).equals(usuario.getSenhaHash()))
                .orElseThrow(() -> new RegraNegocioException("Usuário ou senha inválidos"));
        return dto(u);
    }

    @GetMapping("/api/usuarios")
    public List<Map<String, Object>> listar() {
        return usuarioRepository.findByAtivoTrueOrderByNome().stream().map(this::dto).toList();
    }

    @PostMapping("/api/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> criar(@RequestBody @jakarta.validation.Valid NovoUsuario req) {
        if (usuarioRepository.existsByLoginIgnoreCase(req.login().trim())) {
            throw new RegraNegocioException("Já existe um usuário com o login " + req.login().trim());
        }
        Usuario u = new Usuario();
        u.setLogin(req.login().trim().toLowerCase());
        u.setNome(req.nome().trim());
        u.setSal(UUID.randomUUID().toString().substring(0, 8));
        u.setSenhaHash(hash(u.getSal(), req.senha()));
        usuarioRepository.save(u);
        return dto(u);
    }

    public record TrocarSenhaRequest(@NotBlank(message = "Informe a senha atual") String senhaAtual,
                                     @NotBlank(message = "Informe a senha nova") String senhaNova) {}

    /** Troca a senha exigindo a atual; o sal é renovado junto. */
    @PostMapping("/api/usuarios/{id}/senha")
    public Map<String, Object> trocarSenha(@PathVariable Long id,
                                           @RequestBody @jakarta.validation.Valid TrocarSenhaRequest req) {
        Usuario u = usuarioRepository.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado"));
        if (!hash(u.getSal(), req.senhaAtual()).equals(u.getSenhaHash())) {
            throw new RegraNegocioException("Senha atual incorreta");
        }
        u.setSal(UUID.randomUUID().toString().substring(0, 8));
        u.setSenhaHash(hash(u.getSal(), req.senhaNova()));
        usuarioRepository.save(u);
        return dto(u);
    }

    /** Desativa um operador — nunca o último ativo (senão ninguém entra mais). */
    @PostMapping("/api/usuarios/{id}/desativar")
    public void desativar(@PathVariable Long id) {
        Usuario u = usuarioRepository.findById(id)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new RegraNegocioException("Usuário não encontrado"));
        if (usuarioRepository.findByAtivoTrueOrderByNome().size() <= 1) {
            throw new RegraNegocioException(
                    "Não dá para desativar o último operador ativo — o sistema ficaria sem login");
        }
        u.setAtivo(false);
        usuarioRepository.save(u);
    }

    private Map<String, Object> dto(Usuario u) {
        return Map.of("id", u.getId(), "login", u.getLogin(), "nome", u.getNome());
    }

    static String hash(String sal, String senha) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((sal + senha).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
