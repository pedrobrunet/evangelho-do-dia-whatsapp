package com.botwpp.evangelho.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de integracao com o WhatsApp (prefixo "whatsapp" no application.yml).
 *
 * Suporta dois modos:
 *  - EVOLUTION: monta o payload no formato da Evolution API (/message/sendText/{instancia});
 *  - WEBHOOK:   faz um POST generico com {"destino": "...", "mensagem": "..."}.
 */
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsappProperties {

    public enum Provider { EVOLUTION, WEBHOOK }

    private Provider provider = Provider.EVOLUTION;

    /** URL base da Evolution API (ex.: http://localhost:8080) ou URL completa do webhook. */
    private String apiUrl = "http://localhost:8080";

    /** Nome da instancia conectada na Evolution API. Ignorado no modo WEBHOOK. */
    private String instancia = "default";

    /** Valor enviado no header "apikey" (Evolution) ou "Authorization" (webhook). */
    private String apiKey = "";

    /** Quando true, apenas registra a mensagem em log sem chamar a API externa. */
    private boolean simular = true;

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getInstancia() {
        return instancia;
    }

    public void setInstancia(String instancia) {
        this.instancia = instancia;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public boolean isSimular() {
        return simular;
    }

    public void setSimular(boolean simular) {
        this.simular = simular;
    }
}
