package com.botwpp.evangelho.scheduler;

import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.repository.AgendamentoRepository;
import com.botwpp.evangelho.service.AgendamentoService;
import com.botwpp.evangelho.service.AutenticacaoService;
import com.botwpp.evangelho.service.EnvioEvangelhoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Agendador dos envios diarios, de todas as contas.
 *
 * O cron dispara a cada minuto e o metodo decide quais agendamentos chegaram
 * na hora. Cada envio sai pela sessao de WhatsApp do dono do agendamento —
 * e o unico ponto do sistema que percorre contas diferentes.
 *
 * Guarda de idempotencia: {@code ultimoEnvio} garante um unico envio por dia
 * por agendamento, mesmo que a aplicacao reinicie.
 */
@Component
public class EnvioScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnvioScheduler.class);

    private final AgendamentoRepository repository;
    private final EnvioEvangelhoService envioService;
    private final AutenticacaoService autenticacaoService;
    private final Clock clock;

    public EnvioScheduler(AgendamentoRepository repository,
                          EnvioEvangelhoService envioService,
                          AutenticacaoService autenticacaoService,
                          Clock clock) {
        this.repository = repository;
        this.envioService = envioService;
        this.autenticacaoService = autenticacaoService;
        this.clock = clock;
    }

    /**
     * Executa no segundo 0 de cada minuto, no fuso configurado em app.timezone.
     */
    @Scheduled(cron = "0 * * * * *", zone = "${app.timezone:America/Sao_Paulo}")
    public void verificarEDisparar() {
        LocalDate hoje = LocalDate.now(clock);
        LocalTime agora = LocalTime.now(clock);

        for (Agendamento agendamento : repository.listarTodos()) {
            if (!deveDisparar(agendamento, hoje, agora)) {
                continue;
            }

            Optional<Usuario> dono = autenticacaoService.buscarPorId(agendamento.getUsuarioId());
            if (dono.isEmpty()) {
                // Conta removida com agendamento orfao: nao ha sessao por onde enviar.
                log.warn("Agendamento {} sem conta dona; ignorado.", agendamento.getId());
                continue;
            }

            log.info("Horario atingido para {}. Iniciando o envio.", agendamento.descricao());
            try {
                envioService.enviar(dono.get().getInstancia(), agendamento);
            } catch (RuntimeException e) {
                // Nao propaga: uma excecao aqui interromperia os demais agendamentos
                // desta rodada e os proximos ciclos do scheduler.
                log.error("Envio automatico de {} falhou. Nova tentativa enquanto durar a janela.",
                        agendamento.descricao(), e);
            }
        }
    }

    private boolean deveDisparar(Agendamento agendamento, LocalDate hoje, LocalTime agora) {
        if (!agendamento.isAtivo()
                || agendamento.getHorarioEnvio() == null
                || agendamento.getGrupoId() == null
                || agendamento.getGrupoId().isBlank()) {
            return false;
        }

        // Ja enviou hoje: nada a fazer.
        if (hoje.equals(agendamento.getUltimoEnvio())) {
            return false;
        }

        // Dispara dentro da janela [horario, horario + tolerancia], o que cobre
        // retry apos falha e aplicacao que subiu pouco depois do horario.
        LocalTime inicio = agendamento.getHorarioEnvio();
        LocalTime limite = inicio.plusMinutes(AgendamentoService.JANELA_TOLERANCIA_MINUTOS);

        if (limite.isBefore(inicio)) {
            // Janela cruza a meia-noite (ex.: 23:55 + 10min).
            return !agora.isBefore(inicio) || !agora.isAfter(limite);
        }
        return !agora.isBefore(inicio) && !agora.isAfter(limite);
    }
}
