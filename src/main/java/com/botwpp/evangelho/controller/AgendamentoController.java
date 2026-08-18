package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.config.UsuarioLogado;
import com.botwpp.evangelho.dto.AgendamentoRequest;
import com.botwpp.evangelho.dto.ProximoEnvio;
import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.service.AgendamentoService;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import jakarta.servlet.http.HttpServletRequest;
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
 * CRUD dos agendamentos e leitura da fila.
 *
 * Toda operacao e escopada no usuario da sessao: o id do dono nunca vem do
 * payload, sempre da sessao, para que ninguem opere sobre conta alheia.
 */
@RestController
@RequestMapping("/api/agendamentos")
public class AgendamentoController {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoController.class);

    private final AgendamentoService agendamentoService;
    private final EnvioEvangelhoService envioService;
    private final UsuarioLogado usuarioLogado;

    public AgendamentoController(AgendamentoService agendamentoService,
                                 EnvioEvangelhoService envioService,
                                 UsuarioLogado usuarioLogado) {
        this.agendamentoService = agendamentoService;
        this.envioService = envioService;
        this.usuarioLogado = usuarioLogado;
    }

    /** GET /api/agendamentos — os da conta logada, ordenados por horario. */
    @GetMapping
    public ResponseEntity<List<Agendamento>> listar(HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        return ResponseEntity.ok(agendamentoService.listar(usuario.getId()));
    }

    /** GET /api/agendamentos/fila — proximos disparos da conta logada. */
    @GetMapping("/fila")
    public ResponseEntity<List<ProximoEnvio>> fila(HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        return ResponseEntity.ok(agendamentoService.proximosEnvios(usuario.getId()));
    }

    /** POST /api/agendamentos */
    @PostMapping
    public ResponseEntity<RespostaApi> criar(@Valid @RequestBody AgendamentoRequest request,
                                             HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        Agendamento criado = agendamentoService.criar(usuario.getId(), request);

        log.info("Agendamento criado: {}", criado.descricao());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RespostaApi.ok("Agendamento criado para as " + request.horarioEnvio() + ".", criado));
    }

    /** PUT /api/agendamentos/{id} */
    @PutMapping("/{id}")
    public ResponseEntity<RespostaApi> atualizar(@PathVariable String id,
                                                 @Valid @RequestBody AgendamentoRequest request,
                                                 HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        Agendamento atualizado = agendamentoService.atualizar(usuario.getId(), id, request);
        return ResponseEntity.ok(RespostaApi.ok("Agendamento atualizado.", atualizado));
    }

    /** POST /api/agendamentos/{id}/alternar — pausa ou reativa. */
    @PostMapping("/{id}/alternar")
    public ResponseEntity<RespostaApi> alternar(@PathVariable String id, HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        Agendamento agendamento = agendamentoService.alternarAtivo(usuario.getId(), id);

        return ResponseEntity.ok(RespostaApi.ok(
                agendamento.isAtivo() ? "Agendamento reativado." : "Agendamento pausado.", agendamento));
    }

    /** DELETE /api/agendamentos/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<RespostaApi> remover(@PathVariable String id, HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        agendamentoService.remover(usuario.getId(), id);
        return ResponseEntity.ok(RespostaApi.ok("Agendamento removido."));
    }

    /** POST /api/agendamentos/{id}/enviar — dispara este agendamento agora. */
    @PostMapping("/{id}/enviar")
    public ResponseEntity<RespostaApi> enviarAgora(@PathVariable String id, HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        Agendamento agendamento = agendamentoService.obrigatorio(usuario.getId(), id);

        String status = envioService.enviar(usuario.getInstancia(), agendamento);
        return ResponseEntity.ok(RespostaApi.ok(status, agendamento));
    }
}
