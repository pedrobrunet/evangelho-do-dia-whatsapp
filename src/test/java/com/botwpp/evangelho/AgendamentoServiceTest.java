package com.botwpp.evangelho;

import com.botwpp.evangelho.dto.ProximoEnvio;
import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.repository.AgendamentoRepository;
import com.botwpp.evangelho.service.AgendamentoService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o calculo da fila de proximos envios.
 */
class AgendamentoServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 18);

    private Clock relogioEm(int hora, int minuto) {
        Instant instante = HOJE.atTime(hora, minuto).atZone(SP).toInstant();
        return Clock.fixed(instante, SP);
    }

    private Agendamento agendamento(String id, String nome, boolean ativo,
                                    LocalTime horario, LocalDate ultimoEnvio) {
        Agendamento a = new Agendamento();
        a.setId(id);
        a.setGrupoNome(nome);
        a.setGrupoId("120363000000000000@g.us");
        a.setAtivo(ativo);
        a.setHorarioEnvio(horario);
        a.setUltimoEnvio(ultimoEnvio);
        return a;
    }

    private AgendamentoService servicoCom(Clock clock, Agendamento... agendamentos) {
        AgendamentoRepository repository = mock(AgendamentoRepository.class);
        when(repository.listar()).thenReturn(List.of(agendamentos));
        return new AgendamentoService(repository, clock);
    }

    @Test
    void deveAgendarParaHojeQuandoOHorarioAindaNaoChegou() {
        AgendamentoService service = servicoCom(relogioEm(6, 0),
                agendamento("a", "Paroquia", true, LocalTime.of(8, 0), null));

        List<ProximoEnvio> fila = service.proximosEnvios();

        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).hoje()).isTrue();
        assertThat(fila.get(0).quando()).isEqualTo(HOJE.atTime(8, 0));
        assertThat(fila.get(0).emMinutos()).isEqualTo(120);
    }

    @Test
    void deveAgendarParaAmanhaQuandoJaEnviouHoje() {
        AgendamentoService service = servicoCom(relogioEm(9, 0),
                agendamento("a", "Paroquia", true, LocalTime.of(8, 0), HOJE));

        List<ProximoEnvio> fila = service.proximosEnvios();

        assertThat(fila.get(0).hoje()).isFalse();
        assertThat(fila.get(0).quando()).isEqualTo(HOJE.plusDays(1).atTime(8, 0));
    }

    @Test
    void deveAgendarParaAmanhaQuandoPassouDaJanelaDeTolerancia() {
        // 08:30 ja excede os 10 minutos de tolerancia do horario das 08:00.
        AgendamentoService service = servicoCom(relogioEm(8, 30),
                agendamento("a", "Paroquia", true, LocalTime.of(8, 0), null));

        assertThat(service.proximosEnvios().get(0).hoje()).isFalse();
    }

    @Test
    void deveManterHojeQuandoAindaDentroDaJanela() {
        // 08:05 continua dentro da tolerancia: o scheduler ainda vai disparar.
        AgendamentoService service = servicoCom(relogioEm(8, 5),
                agendamento("a", "Paroquia", true, LocalTime.of(8, 0), null));

        assertThat(service.proximosEnvios().get(0).hoje()).isTrue();
    }

    @Test
    void naoDeveIncluirAgendamentosPausados() {
        AgendamentoService service = servicoCom(relogioEm(6, 0),
                agendamento("ativo", "Paroquia", true, LocalTime.of(8, 0), null),
                agendamento("pausado", "Familia", false, LocalTime.of(9, 0), null));

        List<ProximoEnvio> fila = service.proximosEnvios();

        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).grupoNome()).isEqualTo("Paroquia");
    }

    @Test
    void deveOrdenarDoMaisProximoAoMaisDistante() {
        AgendamentoService service = servicoCom(relogioEm(10, 0),
                agendamento("noite", "Noite", true, LocalTime.of(20, 0), null),
                agendamento("manha", "Manha", true, LocalTime.of(7, 0), null),
                agendamento("tarde", "Tarde", true, LocalTime.of(12, 0), null));

        List<ProximoEnvio> fila = service.proximosEnvios();

        // As 10:00, o das 07:00 ja passou e cai para amanha; os demais sao hoje.
        assertThat(fila).extracting(ProximoEnvio::grupoNome)
                .containsExactly("Tarde", "Noite", "Manha");
    }

    @Test
    void deveUsarOIdDoGrupoQuandoNaoHaNome() {
        Agendamento semNome = agendamento("a", "", true, LocalTime.of(8, 0), null);
        AgendamentoService service = servicoCom(relogioEm(6, 0), semNome);

        assertThat(service.proximosEnvios().get(0).grupoNome()).isEqualTo("120363000000000000@g.us");
    }
}
