package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.dto.ConfiguracaoRequest;
import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.ConfiguracaoEnvio;
import com.botwpp.evangelho.repository.ConfiguracaoRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalTime;

/**
 * API de configuracao consumida pelo frontend.
 *
 * Expoe somente o que o usuario controla (horario, destino, ativo).
 * Credenciais da Evolution API ficam em variavel de ambiente e nunca
 * transitam por estes endpoints.
 */
@RestController
@RequestMapping("/api/configuracao")
public class ConfiguracaoController {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoController.class);

    private final ConfiguracaoRepository repository;

    public ConfiguracaoController(ConfiguracaoRepository repository) {
        this.repository = repository;
    }

    /** GET /api/configuracao — estado atual exibido ao abrir a pagina. */
    @GetMapping
    public ResponseEntity<ConfiguracaoEnvio> buscar() {
        return ResponseEntity.ok(repository.buscar());
    }

    /** PUT /api/configuracao — grava horario, destino e o liga/desliga. */
    @PutMapping
    public ResponseEntity<RespostaApi> atualizar(@Valid @RequestBody ConfiguracaoRequest request) {
        ConfiguracaoEnvio configuracao = repository.buscar();

        configuracao.setHorarioEnvio(LocalTime.parse(request.horarioEnvio()));
        configuracao.setGrupoId(request.grupoId().trim());
        configuracao.setAtivo(request.ativo());

        repository.salvar(configuracao);
        log.info("Configuracao atualizada: horario={}, ativo={}",
                configuracao.getHorarioEnvio(), configuracao.isAtivo());

        return ResponseEntity.ok(RespostaApi.ok(
                "Configuracao salva. Envio " + (configuracao.isAtivo() ? "ativado" : "desativado")
                        + " para as " + request.horarioEnvio() + ".",
                configuracao));
    }
}
