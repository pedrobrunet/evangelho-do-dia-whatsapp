package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.dto.RespostaApi;
import com.botwpp.evangelho.model.Evangelho;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import com.botwpp.evangelho.service.LiturgiaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de leitura do Evangelho e de disparo manual.
 */
@RestController
@RequestMapping("/api/evangelho")
public class EvangelhoController {

    private final LiturgiaService liturgiaService;
    private final EnvioEvangelhoService envioService;

    public EvangelhoController(LiturgiaService liturgiaService, EnvioEvangelhoService envioService) {
        this.liturgiaService = liturgiaService;
        this.envioService = envioService;
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

    /** POST /api/evangelho/enviar — dispara agora, sem esperar o horario agendado. */
    @PostMapping("/enviar")
    public ResponseEntity<RespostaApi> enviarAgora() {
        String status = envioService.enviarParaGrupoConfigurado();
        return ResponseEntity.ok(RespostaApi.ok(status));
    }
}
