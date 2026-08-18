package com.botwpp.evangelho.service;

import com.botwpp.evangelho.config.WhatsappProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * formatado e faz o POST. Isso mantem a integracao trocavel (Evolution API,
 * webhook proprio, n8n, Z-API...) sem tocar na regra de negocio.
 *
 * SEGURANCA: a apiKey nunca e logada nem devolvida pela API REST.
 */
@Service
public class WhatsappService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappService.class);

    private final RestClient restClient;
    private final WhatsappProperties properties;

    public WhatsappService(RestClient restClient, WhatsappProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Envia a mensagem para o destino informado.
     *
     * @param destino  ID do grupo (…@g.us) ou numero no formato internacional
     * @param mensagem texto ja formatado
     * @throws IllegalStateException quando a API externa recusa a requisicao
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

        String url = montarUrl();
        Map<String, Object> payload = montarPayload(destino, mensagem);

        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                            // Evolution API usa o header "apikey"; webhooks genericos usam Bearer.
                            if (properties.getProvider() == WhatsappProperties.Provider.EVOLUTION) {
                                headers.set("apikey", properties.getApiKey());
                            } else {
                                headers.setBearerAuth(properties.getApiKey());
                            }
                        }
                    })
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Mensagem enviada para {} via {}.", mascarar(destino), properties.getProvider());

        } catch (RestClientResponseException e) {
            // Loga status e corpo da resposta — sem repetir credenciais.
            log.error("A API de WhatsApp respondeu {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(
                    "Falha ao enviar a mensagem (HTTP " + e.getStatusCode().value() + ").", e);
        } catch (Exception e) {
            log.error("Erro de comunicacao com a API de WhatsApp.", e);
            throw new IllegalStateException("Erro de comunicacao com a API de WhatsApp.", e);
        }
    }

    /**
     * EVOLUTION: {apiUrl}/message/sendText/{instancia}
     * WEBHOOK:   a propria apiUrl configurada.
     */
    private String montarUrl() {
        if (properties.getProvider() == WhatsappProperties.Provider.EVOLUTION) {
            String base = properties.getApiUrl().replaceAll("/+$", "");
            return base + "/message/sendText/" + properties.getInstancia();
        }
        return properties.getApiUrl();
    }

    /** Monta o corpo no formato esperado por cada provider. */
    private Map<String, Object> montarPayload(String destino, String mensagem) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (properties.getProvider() == WhatsappProperties.Provider.EVOLUTION) {
            // Formato da Evolution API v2.
            payload.put("number", destino);
            payload.put("text", mensagem);
            payload.put("linkPreview", false);
        } else {
            // Contrato generico, consumido por n8n / Make / webhook proprio.
            payload.put("destino", destino);
            payload.put("mensagem", mensagem);
        }
        return payload;
    }

    /** Evita expor o numero/grupo completo nos logs da aplicacao. */
    private String mascarar(String destino) {
        if (destino.length() <= 6) {
            return "***";
        }
        return destino.substring(0, 4) + "***" + destino.substring(destino.length() - 4);
    }
}
