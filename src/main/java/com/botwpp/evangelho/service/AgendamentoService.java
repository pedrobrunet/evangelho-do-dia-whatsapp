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
 *
 * Todo metodo publico recebe o id do dono: o isolamento entre contas nao pode
 * depender de o controller lembrar de filtrar.
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

    public List<Agendamento> listar(String usuarioId) {
        return repository.listarDoUsuario(usuarioId);
    }

    public Agendamento criar(String usuarioId, AgendamentoRequest request) {
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuarioId(usuarioId);
        aplicar(agendamento, request);
        return repository.inserir(agendamento);
    }

    public Agendamento atualizar(String usuarioId, String id, AgendamentoRequest request) {
        Agendamento agendamento = obrigatorio(usuarioId, id);
        aplicar(agendamento, request);
        repository.atualizar();
        return agendamento;
    }

    /** Alterna ativo/pausado sem exigir o payload completo. */
    public Agendamento alternarAtivo(String usuarioId, String id) {
        Agendamento agendamento = obrigatorio(usuarioId, id);
        agendamento.setAtivo(!agendamento.isAtivo());
        repository.atualizar();
        return agendamento;
    }

    public void remover(String usuarioId, String id) {
        if (!repository.remover(usuarioId, id)) {
            throw new IllegalArgumentException("Agendamento nao encontrado.");
        }
    }

    /**
     * Busca o agendamento dentro da conta informada.
     * A mesma mensagem e usada para "nao existe" e "e de outra conta", para
     * nao revelar a existencia de agendamentos alheios.
     */
    public Agendamento obrigatorio(String usuarioId, String id) {
        return repository.buscar(usuarioId, id)
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
     * Proximos disparos previstos da conta, do mais proximo ao mais distante.
     * Agendamentos pausados ficam de fora — nao ha disparo previsto para eles.
     */
    public List<ProximoEnvio> proximosEnvios(String usuarioId) {
        LocalDateTime agora = LocalDateTime.now(clock);

        return repository.listarDoUsuario(usuarioId).stream()
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
