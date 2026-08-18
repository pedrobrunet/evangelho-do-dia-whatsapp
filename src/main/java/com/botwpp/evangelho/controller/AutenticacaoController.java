package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.config.UsuarioLogado;
import com.botwpp.evangelho.dto.LoginRequest;
import com.botwpp.evangelho.dto.RegistroRequest;
import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.dto.UsuarioResponse;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.service.AutenticacaoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro, login e sessao.
 *
 * As respostas usam UsuarioResponse: o modelo persistido carrega o hash da
 * senha e nunca deve sair pela API.
 */
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;
    private final UsuarioLogado usuarioLogado;

    public AutenticacaoController(AutenticacaoService autenticacaoService, UsuarioLogado usuarioLogado) {
        this.autenticacaoService = autenticacaoService;
        this.usuarioLogado = usuarioLogado;
    }

    /** POST /api/auth/registrar — cria a conta e ja deixa a sessao ativa. */
    @PostMapping("/registrar")
    public ResponseEntity<RespostaApi> registrar(@Valid @RequestBody RegistroRequest request,
                                                 HttpServletRequest http) {
        Usuario usuario = autenticacaoService.registrar(request);
        usuarioLogado.entrar(http, usuario);

        return ResponseEntity.status(HttpStatus.CREATED).body(RespostaApi.ok(
                "Conta criada! Enviamos um e-mail de confirmacao para " + usuario.getEmail() + ".",
                UsuarioResponse.de(usuario)));
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<RespostaApi> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletRequest http) {
        Usuario usuario = autenticacaoService.autenticar(request);
        usuarioLogado.entrar(http, usuario);

        return ResponseEntity.ok(RespostaApi.ok(
                "Bem-vindo, " + usuario.getNome() + "!", UsuarioResponse.de(usuario)));
    }

    /** POST /api/auth/sair */
    @PostMapping("/sair")
    public ResponseEntity<RespostaApi> sair(HttpServletRequest http) {
        usuarioLogado.sair(http);
        return ResponseEntity.ok(RespostaApi.ok("Sessao encerrada."));
    }

    /**
     * GET /api/auth/eu — quem esta logado.
     * Rota aberta: o painel a consulta ao abrir para decidir entre
     * mostrar a tela de login ou o painel. Sem sessao, responde 204.
     */
    @GetMapping("/eu")
    public ResponseEntity<UsuarioResponse> eu(HttpServletRequest http) {
        try {
            return ResponseEntity.ok(UsuarioResponse.de(usuarioLogado.obrigatorio(http)));
        } catch (IllegalStateException e) {
            return ResponseEntity.noContent().build();
        }
    }
}
