package com.botwpp.evangelho.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Exige sessao autenticada em toda a API.
 *
 * Substitui o token unico da versao anterior: agora o acesso e por conta, e
 * cada requisicao carrega a identidade do dono dos dados. Sem isso, qualquer
 * pessoa com a URL publica controlaria os WhatsApps conectados.
 */
@Component
public class SessaoFilter extends OncePerRequestFilter {

    /** Rotas abertas: sao justamente as que criam a sessao. */
    private static final Set<String> LIBERADAS = Set.of(
            "/api/auth/registrar",
            "/api/auth/login",
            "/api/auth/eu"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI();
        // Arquivos estaticos seguem livres; a tela de login precisa carregar.
        return !caminho.startsWith("/api/") || LIBERADAS.contains(caminho);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        HttpSession sessao = request.getSession(false);
        boolean autenticado = sessao != null && sessao.getAttribute(UsuarioLogado.ATRIBUTO) != null;

        if (!autenticado) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(
                    "{\"sucesso\":false,\"mensagem\":\"Faca login para continuar.\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
