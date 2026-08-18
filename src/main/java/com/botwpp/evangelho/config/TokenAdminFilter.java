package com.botwpp.evangelho.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protecao minima dos endpoints de escrita da API.
 *
 * Como o projeto e open source e pode ser exposto na internet, os endpoints
 * que alteram configuracao ou disparam envio exigem o header
 * {@code X-Admin-Token} quando a variavel ADMIN_TOKEN estiver definida.
 *
 * Se ADMIN_TOKEN estiver vazio (padrao para uso local), o filtro fica inerte —
 * mas a aplicacao registra um aviso na subida.
 */
@Component
public class TokenAdminFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenAdminFilter.class);
    private static final String HEADER = "X-Admin-Token";

    private final String tokenEsperado;

    public TokenAdminFilter(AppProperties properties) {
        this.tokenEsperado = properties.getAdminToken() == null ? "" : properties.getAdminToken().trim();
        if (this.tokenEsperado.isBlank()) {
            log.warn("ADMIN_TOKEN nao definido: a API esta aberta. "
                    + "Defina ADMIN_TOKEN antes de expor esta aplicacao na internet.");
        }
    }

    /** Aplica-se apenas as rotas /api/**; arquivos estaticos seguem livres. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (tokenEsperado.isBlank()) {
            return true;
        }
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String recebido = request.getHeader(HEADER);
        if (recebido == null || !comparacaoSegura(recebido.trim(), tokenEsperado)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, HEADER);
            response.getWriter().write(
                    "{\"sucesso\":false,\"mensagem\":\"Token de administracao invalido ou ausente.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Comparacao em tempo constante — evita timing attack na descoberta do token. */
    private boolean comparacaoSegura(String recebido, String esperado) {
        return MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8),
                esperado.getBytes(StandardCharsets.UTF_8));
    }
}
