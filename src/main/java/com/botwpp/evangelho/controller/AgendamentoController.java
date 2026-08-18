package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.dto.AgendamentoRequest;
import com.botwpp.evangelho.dto.ProximoEnvio;
import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.service.AgendamentoService;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD dos agendamentos e leitura da fila de proximos envios.
 */
@RestController
@RequestMapping("/api/agendamentos")
public class AgendamentoController {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoController.class);

    private final AgendamentoService agendamentoService;
    private final EnvioEvangelhoService envioService;

    public AgendamentoController(AgendamentoService agendamentoService,
                                 EnvioEvangelhoService envioService) {
        this.agendamentoService = agendamentoService;
        this.envioService = envioService;
    }

    /** GET /api/agendamentos — lista completa, ordenada por horario. */
    @GetMapping
    public ResponseEntity<List<Agendamento>> listar() {
        return ResponseEntity.ok(agendamentoService.listar());
    }

    /** GET /api/agendamentos/fila — proximos disparos previstos, do mais proximo ao mais distante. */
    @GetMapping("/fila")
    public ResponseEntity<List<ProximoEnvio>> fila() {
        return ResponseEntity.ok(agendamentoService.proximosEnvios());
    }

    /** POST /api/agendamentos — cria um novo agendamento. */
    @PostMapping
    public ResponseEntity<RespostaApi> criar(@Valid @RequestBody AgendamentoRequest request) {
        Agendamento criado = agendamentoService.criar(request);
        log.info("Agendamento criado: {}", criado.descricao());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespostaApi.ok("Agendamento criado para as " + request.horarioEnvio() + ".", criado));
    }

    /** PUT /api/agendamentos/{id} — edita horario, grupo ou estado. */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaApi> atualizar(@PathVariable String id,
                                                 @Valid @RequestBody AgendamentoRequest request) {
        Agendamento atualizado = agendamentoService.atualizar(id, request);
        return ResponseEntity.ok(RespostaApi.ok("Agendamento atualizado.", atualizado));
    }

    /** POST /api/agendamentos/{id}/alternar — pausa ou reativa sem abrir o formulario. */
    @PostMapping("/{id}/alternar")
    public ResponseEntity<RespostaApi> alternar(@PathVariable String id) {
        Agendamento agendamento = agendamentoService.alternarAtivo(id);
        return ResponseEntity.ok(RespostaApi.ok(
                agendamento.isAtivo() ? "Agendamento reativado." : "Agendamento pausado.", agendamento));
    }

    /** DELETE /api/agendamentos/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<RespostaApi> remover(@PathVariable String id) {
        agendamentoService.remover(id);
        return ResponseEntity.ok(RespostaApi.ok("Agendamento removido."));
    }

    /** POST /api/agendamentos/{id}/enviar — dispara este agendamento agora. */
    @PostMapping("/{id}/enviar")
    public ResponseEntity<RespostaApi> enviarAgora(@PathVariable String id) {
        Agendamento agendamento = agendamentoService.obrigatorio(id);
        String status = envioService.enviar(agendamento);
        return ResponseEntity.ok(RespostaApi.ok(status, agendamento));
    }
}
