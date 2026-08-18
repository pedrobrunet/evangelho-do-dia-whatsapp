package com.botwpp.evangelho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicacao.
 *
 * {@code @EnableScheduling} liga o pool de agendamento do Spring, necessario
 * para que os metodos anotados com {@code @Scheduled} (ver EnvioScheduler)
 * sejam executados.
 */
@SpringBootApplication
@EnableScheduling
public class EvangelhoApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvangelhoApplication.class, args);
    }
}
