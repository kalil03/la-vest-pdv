package br.com.loja.pdv.service;

import br.com.loja.pdv.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * O usuário "admin" nasce da migration V6 com senha padrão conhecida (o hash
 * está público no repositório). Este runner o aposenta sem risco de trancar a
 * loja para fora: enquanto o admin de fábrica for o único login ativo, apenas
 * avisa no log; assim que existir outro operador ativo (cadastrado em
 * Ajustes), desativa o admin no boot seguinte. Se a senha do admin já foi
 * trocada (hash diferente do padrão), não mexe em nada.
 */
@Component
public class AdminPadraoRunner implements ApplicationRunner {

    /** sha256('nexopdv' + 'admin') — exatamente o hash semeado pela V6. */
    static final String HASH_PADRAO =
            "7c641c51f0274e48e5d053066f80986d13d05dfed7a403a529666da09dcc275a";

    private static final Logger log = LoggerFactory.getLogger(AdminPadraoRunner.class);

    private final UsuarioRepository usuarios;

    public AdminPadraoRunner(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public void run(ApplicationArguments args) {
        usuarios.findByLoginIgnoreCaseAndAtivoTrue("admin")
                .filter(u -> HASH_PADRAO.equals(u.getSenhaHash()))
                .ifPresent(admin -> {
                    if (usuarios.countByAtivoTrueAndLoginNot("admin") > 0) {
                        admin.setAtivo(false);
                        usuarios.save(admin);
                        log.info("login 'admin' de fábrica desativado — já existe outro operador cadastrado");
                    } else {
                        log.warn("o login 'admin' ainda usa a senha padrão de fábrica; cadastre um operador "
                                + "em Ajustes e o admin será desativado automaticamente no próximo início");
                    }
                });
    }
}
