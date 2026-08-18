package com.botwpp.evangelho.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades gerais da aplicacao (prefixo "app" no application.yml).
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Fuso horario usado pelo scheduler e pela data liturgica. */
    private String timezone = "America/Sao_Paulo";

    /** Arquivo onde os agendamentos sao persistidos. */
    private String arquivoAgendamentos = "data/agendamentos.json";

    /** Arquivo da versao de agendamento unico, lido apenas para migracao. */
    private String arquivoConfiguracao = "data/configuracao.json";

    public String getArquivoAgendamentos() {
        return arquivoAgendamentos;
    }

    public void setArquivoAgendamentos(String arquivoAgendamentos) {
        this.arquivoAgendamentos = arquivoAgendamentos;
    }

    /** URL da pagina da Cancao Nova usada no scraping. */
    private String liturgiaUrl = "https://liturgiadiaria.cancaonova.com/pp/";

    /** API publica de liturgia usada como fallback quando o scraping falha. */
    private String liturgiaApiUrl = "https://liturgia.up.railway.app/v2/";

    /**
     * Token exigido no header X-Admin-Token para acessar /api/**.
     * Vazio (padrao) libera o acesso — adequado apenas para uso local.
     */
    private String adminToken = "";

    public String getAdminToken() {
        return adminToken;
    }

    public void setAdminToken(String adminToken) {
        this.adminToken = adminToken;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getArquivoConfiguracao() {
        return arquivoConfiguracao;
    }

    public void setArquivoConfiguracao(String arquivoConfiguracao) {
        this.arquivoConfiguracao = arquivoConfiguracao;
    }

    public String getLiturgiaUrl() {
        return liturgiaUrl;
    }

    public void setLiturgiaUrl(String liturgiaUrl) {
        this.liturgiaUrl = liturgiaUrl;
    }

    public String getLiturgiaApiUrl() {
        return liturgiaApiUrl;
    }

    public void setLiturgiaApiUrl(String liturgiaApiUrl) {
        this.liturgiaApiUrl = liturgiaApiUrl;
    }
}
