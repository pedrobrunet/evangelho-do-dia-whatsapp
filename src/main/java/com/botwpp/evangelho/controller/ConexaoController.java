package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.Grupo;
import com.botwpp.evangelho.model.StatusConexao;
import com.botwpp.evangelho.service.ConexaoWhatsappService;
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
 * Endpoints do primeiro passo do painel: conectar o WhatsApp.
 */
@RestController
@RequestMapping("/api/conexao")
public class ConexaoController {

    private final ConexaoWhatsappService conexaoService;

    public ConexaoController(ConexaoWhatsappService conexaoService) {
        this.conexaoService = conexaoService;
    }

    /** GET /api/conexao — estado atual, consultado em polling pelo frontend. */
    @GetMapping
    public ResponseEntity<StatusConexao> status() {
        return ResponseEntity.ok(conexaoService.consultarStatus());
    }

    /**
     * POST /api/conexao/iniciar — gera o QR code ou o codigo de pareamento.
     *
     * Corpo opcional: {"numero": "5511999999999"}. Com numero, a conexao usa
     * codigo de pareamento (sem camera); sem numero, gera QR code.
     */
    @PostMapping("/iniciar")
    public ResponseEntity<StatusConexao> iniciar(@RequestBody(required = false) Map<String, String> corpo) {
        String numero = corpo == null ? null : corpo.get("numero");
        return ResponseEntity.ok(conexaoService.iniciarConexao(numero));
    }

    /** DELETE /api/conexao — encerra a sessao, exigindo novo pareamento. */
    @DeleteMapping
    public ResponseEntity<RespostaApi> desconectar() {
        conexaoService.desconectar();
        return ResponseEntity.ok(RespostaApi.ok("WhatsApp desconectado."));
    }

    /** GET /api/conexao/grupos — alimenta o seletor de destino. */
    @GetMapping("/grupos")
    public ResponseEntity<List<Grupo>> grupos() {
        return ResponseEntity.ok(conexaoService.listarGrupos());
    }
}
