package com.botwpp.evangelho.service;

import com.botwpp.evangelho.config.WhatsappProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Cliente HTTP unico para a Evolution API.
 *
 * Centraliza URL base, header de autenticacao e tratamento de erro, para que
 * os services de dominio (conexao e envio) nao repitam essa mecanica.
 *
 * SEGURANCA: a apiKey vem de variavel de ambiente e nunca e logada.
 */
@Component
public class EvolutionApiClient {

    private static final Logger log = LoggerFactory.getLogger(EvolutionApiClient.class);

    private final RestClient restClient;
    private final WhatsappProperties properties;

    public EvolutionApiClient(RestClient restClient, WhatsappProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** Nome da instancia configurada — usado na maioria das rotas. */
    public String instancia() {
        return properties.getInstancia();
    }

    public boolean estaSimulando() {
        return properties.isSimular();
    }

    public String urlBase() {
        return properties.getApiUrl().replaceAll("/+$", "");
    }

    /**
     * Executa uma chamada e devolve o JSON de resposta.
     *
     * @param metodo  verbo HTTP
     * @param caminho caminho relativo iniciando com "/" (ex.: "/instance/connect/minha")
     * @param corpo   payload JSON, ou null para requisicoes sem corpo
     * @return corpo da resposta como JsonNode, possivelmente nulo
     * @throws IllegalStateException quando a Evolution API recusa ou esta inacessivel
     */
    public JsonNode chamar(HttpMethod metodo, String caminho, Map<String, Object> corpo) {
        String url = urlBase() + caminho;
        try {
            RestClient.RequestBodySpec spec = restClient
                    .method(metodo)
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("apikey", properties.getApiKey());

            if (corpo != null) {
                spec.body(corpo);
            }
            return spec.retrieve().body(JsonNode.class);

        } catch (RestClientResponseException e) {
            log.error("Evolution API respondeu {} em {}: {}",
                    e.getStatusCode(), caminho, e.getResponseBodyAsString());
            throw new IllegalStateException(traduzirErro(e), e);

        } catch (Exception e) {
            log.error("Falha de comunicacao com a Evolution API em {}", caminho, e);
            throw new IllegalStateException(
                    "Nao foi possivel falar com a Evolution API em " + urlBase()
                            + ". Verifique se o servico esta no ar.", e);
        }
    }

    /** Converte o status HTTP em uma orientacao acionavel para quem usa o painel. */
    private String traduzirErro(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        return switch (status) {
            case 401, 403 -> "A Evolution API recusou a credencial. Confira a variavel WHATSAPP_API_KEY.";
            case 404 -> "Recurso nao encontrado na Evolution API. A instancia pode nao existir ainda.";
            default -> "A Evolution API retornou erro HTTP " + status + ".";
        };
    }
}
