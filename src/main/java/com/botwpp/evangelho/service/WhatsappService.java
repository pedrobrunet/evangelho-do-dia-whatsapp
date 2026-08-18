package com.botwpp.evangelho.service;

import com.botwpp.evangelho.config.WhatsappProperties;
import com.botwpp.evangelho.model.StatusConexao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsula o envio da mensagem para o WhatsApp.
 *
 * O service nao conhece o conteudo do Evangelho: recebe destino + texto ja
 * formatado e despacha. Dois modos:
 *  - EVOLUTION: usa a instancia pareada pelo painel (fluxo principal);
 *  - WEBHOOK:   POST generico, para quem prefere n8n / Make / integracao propria.
 *
 * SEGURANCA: a apiKey nunca e logada e o destino aparece mascarado nos logs.
 */
@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    private final EvolutionApiClient client;
    private final ConexaoWhatsappService conexaoService;
    private final RestClient restClient;
    private final WhatsappProperties properties;

    public WhatsappService(EvolutionApiClient client,
                           ConexaoWhatsappService conexaoService,
                           RestClient restClient,
                           WhatsappProperties properties) {
        this.client = client;
        this.conexaoService = conexaoService;
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Envia a mensagem para o destino informado.
     *
     * @param destino  ID do grupo (…@g.us) ou numero no formato internacional
     * @param mensagem texto ja formatado
     * @throws IllegalStateException se o WhatsApp nao estiver conectado ou o envio falhar
     */
    public void enviarMensagem(String destino, String mensagem) {
        if (destino == null || destino.isBlank()) {
            throw new IllegalArgumentException("Destino nao configurado.");
        }

        // Modo simulacao: util em desenvolvimento e em CI, sem credencial real.
        if (properties.isSimular()) {
            log.info("[SIMULACAO] Envio para {} ({} caracteres). Nenhuma chamada externa realizada.",
                    mascarar(destino), mensagem.length());
            log.debug("[SIMULACAO] Conteudo:\n{}", mensagem);
            return;
        }

        if (properties.getProvider() == WhatsappProperties.Provider.EVOLUTION) {
            enviarViaEvolution(destino, mensagem);
        } else {
            enviarViaWebhook(destino, mensagem);
        }
    }

    /**
     * Caminho principal: usa a instancia pareada no painel.
     * Verifica a conexao antes, para devolver um erro compreensivel em vez do
     * HTTP 400 generico que a Evolution API retorna com a sessao fechada.
     */
    private void enviarViaEvolution(String destino, String mensagem) {
        StatusConexao status = conexaoService.consultarStatus();
        if (!status.conectadoComSucesso()) {
            throw new IllegalStateException(
                    "WhatsApp nao esta conectado. Refaca o pareamento no painel antes de enviar.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("number", destino);
        payload.put("text", mensagem);
        payload.put("linkPreview", false);

        client.chamar(HttpMethod.POST, "/message/sendText/" + client.instancia(), payload);
        log.info("Mensagem enviada para {} via Evolution API.", mascarar(destino));
    }

    /** Caminho alternativo: POST generico com {"destino": "...", "mensagem": "..."}. */
    private void enviarViaWebhook(String destino, String mensagem) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("destino", destino);
        payload.put("mensagem", mensagem);

        try {
            restClient.post()
                    .uri(properties.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                            headers.setBearerAuth(properties.getApiKey());
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Mensagem enviada para {} via webhook.", mascarar(destino));

        } catch (RestClientResponseException e) {
            log.error("O webhook respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Falha ao enviar a mensagem (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (Exception e) {
            log.error("Erro de comunicacao com o webhook.", e);
            throw new IllegalStateException("Erro de comunicacao com o webhook configurado.", e);
        }
    }

    /** Evita expor o numero/grupo completo nos logs da aplicacao. */
    private String mascarar(String destino) {
        if (destino.length() <= 6) {
            return "***";
        }
        return destino.substring(0, 4) + "***" + destino.substring(destino.length() - 4);
    }
}
