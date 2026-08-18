package com.botwpp.evangelho;

import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.repository.AgendamentoRepository;
import com.botwpp.evangelho.scheduler.EnvioScheduler;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

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

    private Agendamento agendamento(String id, boolean ativo, LocalTime horario, LocalDate ultimoEnvio) {
        Agendamento a = new Agendamento();
        a.setId(id);
        a.setAtivo(ativo);
        a.setHorarioEnvio(horario);
        a.setGrupoId("120363000000000000@g.us");
        a.setUltimoEnvio(ultimoEnvio);
        return a;
    }

    @Test
    void deveEnviarQuandoChegaOHorarioProgramado() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        Agendamento alvo = agendamento("a", true, LocalTime.of(8, 0), null);
        when(repository.listar()).thenReturn(List.of(alvo));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, times(1)).enviar(alvo);
    }

    @Test
    void naoDeveEnviarQuandoPausado() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.listar()).thenReturn(List.of(agendamento("a", false, LocalTime.of(8, 0), null)));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void naoDeveEnviarDuasVezesNoMesmoDia() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.listar())
                .thenReturn(List.of(agendamento("a", true, LocalTime.of(8, 0), LocalDate.of(2026, 8, 18))));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void naoDeveEnviarForaDaJanelaDeTolerancia() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        when(repository.listar()).thenReturn(List.of(agendamento("a", true, LocalTime.of(8, 0), null)));

        // 08:30 ja passou da janela de 10 minutos apos o horario programado.
        new EnvioScheduler(repository, envio, relogioEm(8, 30)).verificarEDisparar();

        verify(envio, never()).enviar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deveRetentarDentroDaJanelaAposFalha() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
        Agendamento alvo = agendamento("a", true, LocalTime.of(8, 0), null);
        when(repository.listar()).thenReturn(List.of(alvo));
        when(envio.enviar(alvo)).thenThrow(new IllegalStateException("fonte indisponivel"));

        // A excecao nao pode escapar: derrubaria os proximos agendamentos.
        new EnvioScheduler(repository, envio, relogioEm(8, 5)).verificarEDisparar();

        verify(envio, times(1)).enviar(alvo);
    }

    @Test
    void deveDispararApenasOsAgendamentosNoHorario() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);

        Agendamento manha = agendamento("manha", true, LocalTime.of(8, 0), null);
        Agendamento tarde = agendamento("tarde", true, LocalTime.of(12, 0), null);
        Agendamento noite = agendamento("noite", true, LocalTime.of(20, 0), null);
        when(repository.listar()).thenReturn(List.of(manha, tarde, noite));

        new EnvioScheduler(repository, envio, relogioEm(12, 0)).verificarEDisparar();

        verify(envio, times(1)).enviar(tarde);
        verify(envio, never()).enviar(manha);
        verify(envio, never()).enviar(noite);
    }

    @Test
    void falhaDeUmAgendamentoNaoImpedeOsDemais() {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);

        Agendamento primeiro = agendamento("primeiro", true, LocalTime.of(8, 0), null);
        Agendamento segundo = agendamento("segundo", true, LocalTime.of(8, 0), null);
        when(repository.listar()).thenReturn(List.of(primeiro, segundo));
        when(envio.enviar(primeiro)).thenThrow(new IllegalStateException("grupo invalido"));

        new EnvioScheduler(repository, envio, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, times(1)).enviar(primeiro);
        verify(envio, times(1)).enviar(segundo);
    }
}
