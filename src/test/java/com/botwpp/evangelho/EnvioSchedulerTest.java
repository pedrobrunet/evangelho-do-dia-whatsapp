package com.botwpp.evangelho;

import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.repository.AgendamentoRepository;
import com.botwpp.evangelho.scheduler.EnvioScheduler;
import com.botwpp.evangelho.service.AutenticacaoService;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    private AgendamentoRepository repository = mock(AgendamentoRepository.class);
    private EnvioEvangelhoService envio = mock(EnvioEvangelhoService.class);
    private AutenticacaoService autenticacao = mock(AutenticacaoService.class);

    /** Constroi um Clock parado no horario informado (data fixa de referencia). */
    private Clock relogioEm(int hora, int minuto) {
        Instant instante = LocalDate.of(2026, 8, 18)
                .atTime(hora, minuto)
                .atZone(SP)
                .toInstant();
        return Clock.fixed(instante, SP);
    }

    private Usuario usuario(String id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Fulano");
        u.setEmail(id + "@exemplo.com");
        return u;
    }

    private Agendamento agendamento(String id, String usuarioId, boolean ativo,
                                    LocalTime horario, LocalDate ultimoEnvio) {
        Agendamento a = new Agendamento();
        a.setId(id);
        a.setUsuarioId(usuarioId);
        a.setAtivo(ativo);
        a.setHorarioEnvio(horario);
        a.setGrupoId("120363000000000000@g.us");
        a.setUltimoEnvio(ultimoEnvio);
        return a;
    }

    /** Registra o dono e devolve o scheduler pronto. */
    private EnvioScheduler schedulerEm(int hora, int minuto, String... donos) {
        for (String dono : donos) {
            when(autenticacao.buscarPorId(dono)).thenReturn(Optional.of(usuario(dono)));
        }
        return new EnvioScheduler(repository, envio, autenticacao, relogioEm(hora, minuto));
    }

    @Test
    void deveEnviarQuandoChegaOHorarioProgramado() {
        Agendamento alvo = agendamento("a", "dono", true, LocalTime.of(8, 0), null);
        when(repository.listarTodos()).thenReturn(List.of(alvo));

        schedulerEm(8, 0, "dono").verificarEDisparar();

        verify(envio, times(1)).enviar(anyString(), eqAgendamento(alvo));
    }

    @Test
    void naoDeveEnviarQuandoPausado() {
        when(repository.listarTodos())
                .thenReturn(List.of(agendamento("a", "dono", false, LocalTime.of(8, 0), null)));

        schedulerEm(8, 0, "dono").verificarEDisparar();

        verify(envio, never()).enviar(anyString(), any());
    }

    @Test
    void naoDeveEnviarDuasVezesNoMesmoDia() {
        when(repository.listarTodos()).thenReturn(
                List.of(agendamento("a", "dono", true, LocalTime.of(8, 0), LocalDate.of(2026, 8, 18))));

        schedulerEm(8, 0, "dono").verificarEDisparar();

        verify(envio, never()).enviar(anyString(), any());
    }

    @Test
    void naoDeveEnviarForaDaJanelaDeTolerancia() {
        when(repository.listarTodos())
                .thenReturn(List.of(agendamento("a", "dono", true, LocalTime.of(8, 0), null)));

        // 08:30 ja passou da janela de 10 minutos apos o horario programado.
        schedulerEm(8, 30, "dono").verificarEDisparar();

        verify(envio, never()).enviar(anyString(), any());
    }

    @Test
    void deveRetentarDentroDaJanelaAposFalha() {
        Agendamento alvo = agendamento("a", "dono", true, LocalTime.of(8, 0), null);
        when(repository.listarTodos()).thenReturn(List.of(alvo));
        when(envio.enviar(anyString(), any()))
                .thenThrow(new IllegalStateException("fonte indisponivel"));

        // A excecao nao pode escapar: derrubaria os proximos agendamentos.
        schedulerEm(8, 5, "dono").verificarEDisparar();

        verify(envio, times(1)).enviar(anyString(), any());
    }

    @Test
    void deveDispararApenasOsAgendamentosNoHorario() {
        Agendamento manha = agendamento("manha", "dono", true, LocalTime.of(8, 0), null);
        Agendamento tarde = agendamento("tarde", "dono", true, LocalTime.of(12, 0), null);
        Agendamento noite = agendamento("noite", "dono", true, LocalTime.of(20, 0), null);
        when(repository.listarTodos()).thenReturn(List.of(manha, tarde, noite));

        schedulerEm(12, 0, "dono").verificarEDisparar();

        verify(envio, times(1)).enviar(anyString(), eqAgendamento(tarde));
        verify(envio, never()).enviar(anyString(), eqAgendamento(manha));
        verify(envio, never()).enviar(anyString(), eqAgendamento(noite));
    }

    @Test
    void falhaDeUmAgendamentoNaoImpedeOsDemais() {
        Agendamento primeiro = agendamento("primeiro", "dono", true, LocalTime.of(8, 0), null);
        Agendamento segundo = agendamento("segundo", "dono", true, LocalTime.of(8, 0), null);
        when(repository.listarTodos()).thenReturn(List.of(primeiro, segundo));
        when(envio.enviar(anyString(), eqAgendamento(primeiro)))
                .thenThrow(new IllegalStateException("grupo invalido"));

        schedulerEm(8, 0, "dono").verificarEDisparar();

        verify(envio, times(1)).enviar(anyString(), eqAgendamento(primeiro));
        verify(envio, times(1)).enviar(anyString(), eqAgendamento(segundo));
    }

    @Test
    void deveEnviarCadaAgendamentoPelaSessaoDoSeuDono() {
        Agendamento deAna = agendamento("a1", "ana", true, LocalTime.of(8, 0), null);
        Agendamento deBia = agendamento("b1", "bia", true, LocalTime.of(8, 0), null);
        when(repository.listarTodos()).thenReturn(List.of(deAna, deBia));

        schedulerEm(8, 0, "ana", "bia").verificarEDisparar();

        // Cada envio sai pela conta dona — nunca pela sessao da outra.
        verify(envio, times(1)).enviar(eq(usuario("ana").getInstancia()), eqAgendamento(deAna));
        verify(envio, times(1)).enviar(eq(usuario("bia").getInstancia()), eqAgendamento(deBia));
    }

    @Test
    void deveIgnorarAgendamentoOrfaoDeContaRemovida() {
        Agendamento orfao = agendamento("x", "conta-apagada", true, LocalTime.of(8, 0), null);
        when(repository.listarTodos()).thenReturn(List.of(orfao));
        when(autenticacao.buscarPorId("conta-apagada")).thenReturn(Optional.empty());

        new EnvioScheduler(repository, envio, autenticacao, relogioEm(8, 0)).verificarEDisparar();

        verify(envio, never()).enviar(anyString(), any());
    }

    /** Compara agendamentos pelo id, que e o que os distingue nos testes. */
    private Agendamento eqAgendamento(Agendamento esperado) {
        return org.mockito.ArgumentMatchers.argThat(a -> a != null && a.getId().equals(esperado.getId()));
    }
}
