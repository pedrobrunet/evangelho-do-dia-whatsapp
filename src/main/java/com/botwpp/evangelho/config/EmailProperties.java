package com.botwpp.evangelho.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracao de envio de e-mail (prefixo "email" no application.yml).
 *
 * SEGURANCA: a apiKey vem de variavel de ambiente e nunca e logada.
 */
@ConfigurationProperties(prefix = "email")
public class EmailProperties {

    /** Chave da API da Resend. Vazia desabilita o envio (fica so no log). */
    private String apiKey = "";

    /**
     * Remetente no formato "Nome <endereco@dominio>".
     * Sem um dominio verificado na Resend, o unico remetente aceito e
     * onboarding@resend.dev, que so entrega para o e-mail dono da conta.
     */
    private String remetente = "Evangelho do Dia <onboarding@resend.dev>";

    /** URL do painel usada nos links dos e-mails. */
    private String urlPainel = "http://localhost:8081";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getRemetente() {
        return remetente;
    }

    public void setRemetente(String remetente) {
        this.remetente = remetente;
    }

    public String getUrlPainel() {
        return urlPainel;
    }

    public void setUrlPainel(String urlPainel) {
        this.urlPainel = urlPainel;
    }
}
