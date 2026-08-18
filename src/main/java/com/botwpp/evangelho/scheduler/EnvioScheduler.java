package com.botwpp.evangelho.scheduler;

import com.botwpp.evangelho.model.ConfiguracaoEnvio;
import com.botwpp.evangelho.repository.ConfiguracaoRepository;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Agendador do envio diario.
 *
 * O cron dispara a cada minuto e o metodo decide se e hora de enviar.
 * Essa abordagem (em vez de um cron fixo) permite que o usuario troque o
 * horario pelo frontend sem reiniciar a aplicacao nem reprogramar o agendamento.
 *
 * Guarda de idempotencia: {@code ultimoEnvio} garante um unico envio por dia,
 * mesmo que a aplicacao reinicie ou o minuto seja avaliado duas vezes.
 */
@Component
public class EnvioScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnvioScheduler.class);

    /**
     * Janela em minutos apos o horario programado em que o envio ainda e valido.
     * Cobre dois casos: falha temporaria da fonte/API (retry no minuto seguinte)
     * e aplicacao que subiu poucos minutos depois do horario.
     */
    private static final int JANELA_TOLERANCIA_MINUTOS = 10;

    private final ConfiguracaoRepository repository;
    private final EnvioEvangelhoService envioService;
    private final Clock clock;

    public EnvioScheduler(ConfiguracaoRepository repository,
                          EnvioEvangelhoService envioService,
                          Clock clock) {
        this.repository = repository;
        this.envioService = envioService;
        this.clock = clock;
    }

    /**
     * Executa no segundo 0 de cada minuto, no fuso configurado em app.timezone.
     */
    @Scheduled(cron = "0 * * * * *", zone = "${app.timezone:America/Sao_Paulo}")
    public void verificarEDisparar() {
        ConfiguracaoEnvio configuracao = repository.buscar();

        if (!configuracao.isAtivo()) {
            return;
        }
        if (configuracao.getHorarioEnvio() == null || configuracao.getGrupoId().isBlank()) {
            return;
        }

        LocalDate hoje = LocalDate.now(clock);
        LocalTime agora = LocalTime.now(clock);

        // Ja enviou hoje: nada a fazer.
        if (hoje.equals(configuracao.getUltimoEnvio())) {
            return;
        }

        // Envia dentro da janela [horario, horario + tolerancia].
        LocalTime inicio = configuracao.getHorarioEnvio();
        LocalTime limite = inicio.plusMinutes(JANELA_TOLERANCIA_MINUTOS);

        boolean dentroDaJanela = !agora.isBefore(inicio) && !agora.isAfter(limite)
                // Trata a virada de meia-noite (ex.: 23:55 + 10min).
                || (limite.isBefore(inicio) && (!agora.isBefore(inicio) || !agora.isAfter(limite)));

        if (!dentroDaJanela) {
            return;
        }

        log.info("Horario programado atingido ({}). Iniciando o envio do Evangelho.",
                configuracao.getHorarioEnvio());
        try {
            envioService.enviarParaGrupoConfigurado();
        } catch (RuntimeException e) {
            // Nao propaga: uma excecao aqui interromperia os proximos agendamentos.
            log.error("Envio automatico falhou. Sera tentado novamente no proximo minuto.", e);
        }
    }
}
