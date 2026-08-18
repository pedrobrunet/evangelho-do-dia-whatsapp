package com.botwpp.evangelho;

import com.botwpp.evangelho.model.ConfiguracaoEnvio;
import com.botwpp.evangelho.repository.ConfiguracaoRepository;
import com.botwpp.evangelho.scheduler.EnvioScheduler;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre as decisoes do scheduler com um Clock fixo — sem esperar o relogio real.
 */
class EnvioSchedulerTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    /** Constroi um Clock parado no horario informado (data fixa de referencia). */
    private Clock relogioEm(int hora, int minuto) {
        Instant instante = LocalDate.of(2026, 8, 18)
                .atTime(hora, minuto)
                .atZone(SP)
                .toInstant();
        return Clock.fixed(instante, SP);
    }

    private ConfiguracaoEnvio configuracao(boolean ativo, LocalTime horario, LocalDate ultimoEnvio) {
        ConfiguracaoEnvio config = new ConfiguracaoEnvio();
        config.setAtivo(ativo);
        config.setHorarioEnvio(horario);
        config.setGrupoId("120363000000000000@g.us");
        config.setUltimoEnvio(ultimoEnvio);
        return config;
    }

    @Test
    void deveEnviarQuandoChegaOHorarioProgramado() {
        ConfiguracaoRepository repository = mock(ConfiguracaoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.buscar()).thenReturn(configuracao(true, LocalTime.of(8, 0), null));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, times(1)).enviarParaGrupoConfigurado();
    }

    @Test
    void naoDeveEnviarQuandoDesativado() {
        ConfiguracaoRepository repository = mock(ConfiguracaoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.buscar()).thenReturn(configuracao(false, LocalTime.of(8, 0), null));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, never()).enviarParaGrupoConfigurado();
    }

    @Test
    void naoDeveEnviarDuasVezesNoMesmoDia() {
        ConfiguracaoRepository repository = mock(ConfiguracaoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.buscar())
                .thenReturn(configuracao(true, LocalTime.of(8, 0), LocalDate.of(2026, 8, 18)));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, never()).enviarParaGrupoConfigurado();
    }

    @Test
    void naoDeveEnviarForaDaJanelaDeTolerancia() {
        ConfiguracaoRepository repository = mock(ConfiguracaoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.buscar()).thenReturn(configuracao(true, LocalTime.of(8, 0), null));

        // 08:30 ja passou da janela de 10 minutos apos o horario programado.
        new EnvioScheduler(repository, envio, relogioEm(8, 30)).verificarEDisparar();

        verify(envio, never()).enviarParaGrupoConfigurado();
    }

    @Test
    void deveRetentarDentroDaJanelaAposFalha() {
        ConfiguracaoRepository repository = mock(ConfiguracaoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.buscar()).thenReturn(configuracao(true, LocalTime.of(8, 0), null));
        when(envio.enviarParaGrupoConfigurado()).thenThrow(new IllegalStateException("fonte indisponivel"));

        EnvioScheduler scheduler = new EnvioScheduler(repository, envio, relogioEm(8, 5));

        // A excecao nao pode escapar: derrubaria os proximos agendamentos.
        scheduler.verificarEDisparar();

        verify(envio, times(1)).enviarParaGrupoConfigurado();
    }
}
