package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.config.UsuarioLogado;
import com.botwpp.evangelho.dto.EnvioManualRequest;
import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.Evangelho;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import com.botwpp.evangelho.service.LiturgiaService;
import com.botwpp.evangelho.model.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de leitura do Evangelho do dia.
 * O disparo manual vive em AgendamentoController, pois depende de um agendamento.
 */
@RestController
@RequestMapping("/api/evangelho")
public class EvangelhoController {

    private final LiturgiaService liturgiaService;
    private final EnvioEvangelhoService envioService;
    private final UsuarioLogado usuarioLogado;

    public EvangelhoController(LiturgiaService liturgiaService,
                               EnvioEvangelhoService envioService,
                               UsuarioLogado usuarioLogado) {
        this.liturgiaService = liturgiaService;
        this.envioService = envioService;
        this.usuarioLogado = usuarioLogado;
    }

    /** GET /api/evangelho/hoje — dados estruturados do Evangelho do dia. */
    @GetMapping("/hoje")
    public ResponseEntity<Evangelho> hoje() {
        return ResponseEntity.ok(liturgiaService.buscarEvangelhoDoDia());
    }

    /** GET /api/evangelho/previa — mensagem ja formatada, como chegara no WhatsApp. */
    @GetMapping("/previa")
    public ResponseEntity<RespostaApi> previa() {
        String mensagem = envioService.previsualizarMensagem();
        return ResponseEntity.ok(RespostaApi.ok("Previa gerada.", Map.of("mensagem", mensagem)));
    }

    /** POST /api/evangelho/recarregar — limpa o cache do dia e busca de novo na fonte. */
    @PostMapping("/recarregar")
    public ResponseEntity<RespostaApi> recarregar() {
        Evangelho evangelho = liturgiaService.recarregar();
        return ResponseEntity.ok(RespostaApi.ok("Conteudo recarregado da fonte.", evangelho));
    }

    /**
     * POST /api/evangelho/enviar — envio avulso para um grupo.
     * Independe de agendamento e nao altera a programacao automatica.
     */
    @PostMapping("/enviar")
    public ResponseEntity<RespostaApi> enviarAgora(@Valid @RequestBody EnvioManualRequest request,
                                                   HttpServletRequest http) {
        Usuario usuario = usuarioLogado.obrigatorio(http);
        String status = envioService.enviarAvulso(usuario.getInstancia(), request.grupoId().trim());
        return ResponseEntity.ok(RespostaApi.ok(status));
    }
}
