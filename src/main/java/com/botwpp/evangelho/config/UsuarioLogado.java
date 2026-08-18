package com.botwpp.evangelho.config;

import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.service.AutenticacaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * Resolve o usuario da sessao HTTP atual.
 *
 * Centraliza a leitura para que nenhum controller manipule a sessao
 * diretamente — e para que o isolamento entre contas dependa de um
 * unico ponto: cada requisicao so enxerga os dados do dono da sessao.
 */
@Component
public class UsuarioLogado {

    /** Chave do id do usuario na sessao. */
    public static final String ATRIBUTO = "usuarioId";

    private final AutenticacaoService autenticacaoService;

    public UsuarioLogado(AutenticacaoService autenticacaoService) {
        this.autenticacaoService = autenticacaoService;
    }

    /**
     * Usuario da requisicao corrente.
     *
     * @throws IllegalStateException quando nao ha sessao valida — o filtro
     *         normalmente barra antes, mas a checagem evita que um endpoint
     *         novo fique aberto por esquecimento
     */
    public Usuario obrigatorio(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao == null) {
            throw new IllegalStateException("Sessao expirada. Entre novamente.");
        }

        Object id = sessao.getAttribute(ATRIBUTO);
        if (id == null) {
            throw new IllegalStateException("Sessao expirada. Entre novamente.");
        }

        return autenticacaoService.buscarPorId(id.toString())
                .orElseThrow(() -> new IllegalStateException("Conta nao encontrada. Entre novamente."));
    }

    /** Registra o login na sessao. */
    public void entrar(HttpServletRequest request, Usuario usuario) {
        // Sessao nova a cada login: impede fixacao de sessao, em que um id
        // conhecido de antemao passaria a valer como autenticado.
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        request.getSession(true).setAttribute(ATRIBUTO, usuario.getId());
    }

    public void sair(HttpServletRequest request) {
        HttpSession sessao = request.getSession(false);
        if (sessao != null) {
            sessao.invalidate();
        }
    }
}
