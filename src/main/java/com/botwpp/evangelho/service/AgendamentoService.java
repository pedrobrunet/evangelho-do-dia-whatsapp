package com.botwpp.evangelho.service;

import com.botwpp.evangelho.dto.AgendamentoRequest;
import com.botwpp.evangelho.dto.ProximoEnvio;
import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.repository.AgendamentoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Regras dos agendamentos e montagem da fila de proximos envios.
 */
@Service
public class AgendamentoService {

    /**
     * Janela em minutos apos o horario em que o envio ainda e considerado valido.
     * Precisa acompanhar a mesma constante do EnvioScheduler, senao a fila
     * mostraria um disparo que o scheduler ja descartou.
     */
    public static final int JANELA_TOLERANCIA_MINUTOS = 10;

    private final AgendamentoRepository repository;
    private final Clock clock;

    public AgendamentoService(AgendamentoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<Agendamento> listar() {
        return repository.listar();
    }

    public Agendamento criar(AgendamentoRequest request) {
        Agendamento agendamento = new Agendamento();
        aplicar(agendamento, request);
        return repository.inserir(agendamento);
    }

    public Agendamento atualizar(String id, AgendamentoRequest request) {
        Agendamento agendamento = obrigatorio(id);
        aplicar(agendamento, request);
        repository.atualizar();
        return agendamento;
    }

    /** Alterna ativo/pausado sem exigir o payload completo. */
    public Agendamento alternarAtivo(String id) {
        Agendamento agendamento = obrigatorio(id);
        agendamento.setAtivo(!agendamento.isAtivo());
        repository.atualizar();
        return agendamento;
    }

    public void remover(String id) {
        if (!repository.remover(id)) {
            throw new IllegalArgumentException("Agendamento nao encontrado.");
        }
    }

    public Agendamento obrigatorio(String id) {
        return repository.buscar(id)
                .orElseThrow(() -> new IllegalArgumentException("Agendamento nao encontrado."));
    }

    private void aplicar(Agendamento agendamento, AgendamentoRequest request) {
        agendamento.setHorarioEnvio(LocalTime.parse(request.horarioEnvio()));
        agendamento.setGrupoId(request.grupoId().trim());
        agendamento.setGrupoNome(request.grupoNome() == null ? "" : request.grupoNome().trim());
        agendamento.setAtivo(request.ativo());
    }

    // ------------------------------------------------------------------
    // Fila
    // ------------------------------------------------------------------

    /**
     * Proximos disparos previstos, do mais proximo ao mais distante.
     * Agendamentos pausados ficam de fora — nao ha disparo previsto para eles.
     */
    public List<ProximoEnvio> proximosEnvios() {
        LocalDateTime agora = LocalDateTime.now(clock);

        return repository.listar().stream()
                .filter(Agendamento::isAtivo)
                .map(agendamento -> montar(agendamento, agora))
                .sorted(Comparator.comparing(ProximoEnvio::quando))
                .toList();
    }

    private ProximoEnvio montar(Agendamento agendamento, LocalDateTime agora) {
        LocalDateTime quando = calcularProximoDisparo(agendamento, agora);
        long minutos = Duration.between(agora, quando).toMinutes();

        String nome = (agendamento.getGrupoNome() == null || agendamento.getGrupoNome().isBlank())
                ? agendamento.getGrupoId()
                : agendamento.getGrupoNome();

        return new ProximoEnvio(
                agendamento.getId(),
                nome,
                quando,
                Math.max(0, minutos),
                quando.toLocalDate().equals(agora.toLocalDate()));
    }

    /**
     * Um agendamento dispara uma vez por dia. Ja tendo enviado hoje, ou tendo
     * ultrapassado a janela de tolerancia, o proximo disparo cai no dia seguinte.
     */
    private LocalDateTime calcularProximoDisparo(Agendamento agendamento, LocalDateTime agora) {
        LocalDate hoje = agora.toLocalDate();
        LocalDateTime hojeNoHorario = hoje.atTime(agendamento.getHorarioEnvio());

        boolean jaEnviouHoje = hoje.equals(agendamento.getUltimoEnvio());
        boolean aindaCabeHoje = !agora.isAfter(hojeNoHorario.plusMinutes(JANELA_TOLERANCIA_MINUTOS));

        return (!jaEnviouHoje && aindaCabeHoje) ? hojeNoHorario : hojeNoHorario.plusDays(1);
    }
}
