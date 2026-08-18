package com.botwpp.evangelho.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Beans de infraestrutura compartilhados pelos services.
 */
@Configuration
@EnableConfigurationProperties({AppProperties.class, WhatsappProperties.class})
public class AplicacaoConfig {

    /** Cliente HTTP usado tanto pela API de liturgia quanto pelo envio ao WhatsApp. */
    @Bean
    public RestClient restClient() {
        return RestClient.builder().build();
    }

    /**
     * Clock explicito no fuso configurado.
     * Injetar Clock (em vez de chamar LocalDate.now()) mantem o scheduler testavel.
     */
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.getTimezone()));
    }
}
