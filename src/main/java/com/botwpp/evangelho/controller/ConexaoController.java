package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.config.UsuarioLogado;
import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.Grupo;
import com.botwpp.evangelho.model.StatusConexao;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.service.ConexaoWhatsappService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Conexao do WhatsApp da conta logada.
 *
 * A instancia sempre deriva do usuario da sessao — nunca de um parametro —
 * para que ninguem consiga operar a sessao de WhatsApp de outra conta.
 */
@RestController
@RequestMapping("/api/conexao")
public class ConexaoController {

    private final ConexaoWhatsappService conexaoService;
    private final UsuarioLogado usuarioLogado;

    public ConexaoController(ConexaoWhatsappService conexaoService, UsuarioLogado usuarioLogado) {
        this.conexaoService = conexaoService;
        this.usuarioLogado = usuarioLogado;
    }

    /** GET /api/conexao — estado atual, consultado em polling pelo painel. */
    @GetMapping
    public ResponseEntity<StatusConexao> status(HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        return ResponseEntity.ok(conexaoService.consultarStatus(usuario.getInstancia()));
    }

    /**
     * POST /api/conexao/iniciar — gera o QR code ou o codigo de pareamento.
     *
     * Corpo opcional: {"numero": "5511999999999"}. Com numero, a conexao usa
     * codigo de pareamento (sem camera); sem numero, gera QR code.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<StatusConexao> iniciar(@RequestBody(required = false) Map<String, String> corpo,
                                                 HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        String numero = corpo == null ? null : corpo.get("numero");
        return ResponseEntity.ok(conexaoService.iniciarConexao(usuario.getInstancia(), numero));
    }

    /** DELETE /api/conexao — encerra a sessao, exigindo novo pareamento. */
    @DeleteMapping
    public ResponseEntity<RespostaApi> desconectar(HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        conexaoService.desconectar(usuario.getInstancia());
        return ResponseEntity.ok(RespostaApi.ok("WhatsApp desconectado."));
    }

    /** GET /api/conexao/grupos — alimenta os seletores de destino. */
    @GetMapping("/grupos")
    public ResponseEntity<List<Grupo>> grupos(HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        return ResponseEntity.ok(conexaoService.listarGrupos(usuario.getInstancia()));
    }
}
