package br.com.loja.pdv.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * As respostas da API são dados vivos (saldo, listas). Sem header de cache, o
 * navegador guardava a resposta GET e, depois de receber/reverter, o re-fetch
 * devolvia a versão VELHA — a tela não atualizava. Aqui todo /api/** volta com
 * Cache-Control: no-store, garantindo que a tela sempre veja o estado atual.
 */
@Component
@Order(0)
public class ApiNoCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/api/")) {
            res.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(req, res);
    }
}
