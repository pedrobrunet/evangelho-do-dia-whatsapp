package com.botwpp.evangelho.service;

import com.botwpp.evangelho.model.Agendamento;
import com.botwpp.evangelho.model.Evangelho;
import com.botwpp.evangelho.repository.AgendamentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Orquestra o caso de uso "enviar o Evangelho do dia" para um agendamento:
 * busca o texto, formata a mensagem e delega o envio.
 *
 * Fica entre o scheduler e os services de infraestrutura para que o disparo
 * manual (botao "Enviar agora") e o automatico compartilhem a mesma regra.
 */
@Service
public class EnvioEvangelhoService {

    private static final Logger log = LoggerFactory.getLogger(EnvioEvangelhoService.class);

    private static final DateTimeFormatter DATA_EXTENSO =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final LiturgiaService liturgiaService;
    private final WhatsappService whatsappService;
    private final AgendamentoRepository repository;
    private final Clock clock;

    public EnvioEvangelhoService(LiturgiaService liturgiaService,
                                 WhatsappService whatsappService,
                                 AgendamentoRepository repository,
                                 Clock clock) {
        this.liturgiaService = liturgiaService;
        this.whatsappService = whatsappService;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Executa o envio de um agendamento e registra o resultado nele proprio.
     *
     * @return mensagem de status legivel para o painel
     * @throws IllegalStateException se a busca ou o envio falharem
     */
    public String enviar(Agendamento agendamento) {
        if (agendamento.getGrupoId() == null || agendamento.getGrupoId().isBlank()) {
            throw new IllegalStateException("Agendamento sem grupo de destino.");
        }

        Evangelho evangelho = liturgiaService.buscarEvangelhoDoDia();
        String mensagem = formatarMensagem(evangelho);

        try {
            whatsappService.enviarMensagem(agendamento.getGrupoId(), mensagem);

            agendamento.setUltimoEnvio(LocalDate.now(clock));
            String status = "Enviado em " + LocalDate.now(clock).format(DATA_EXTENSO)
                    + " (" + evangelho.referencia() + ").";
            agendamento.setUltimoStatus(status);
            repository.atualizar();

            log.info("Envio concluido para {}: {}", agendamento.descricao(), status);
            return status;

        } catch (RuntimeException e) {
            // Registra a falha sem marcar ultimoEnvio: o scheduler tentara de novo
            // nos proximos minutos, enquanto durar a janela de tolerancia.
            agendamento.setUltimoStatus("Falha no ultimo envio: " + e.getMessage());
            repository.atualizar();
            throw e;
        }
    }

    /**
     * Monta a mensagem com formatacao do WhatsApp
     * (*negrito*, _italico_) e emojis para leitura no celular.
     */
    public String formatarMensagem(Evangelho evangelho) {
        return """
                📖 *EVANGELHO DO DIA*
                _%s_

                *%s*

                %s

                ✝️ _Palavra da Salvacao._
                🙏 Que Deus abencoe o seu dia!"""
                .formatted(
                        evangelho.data().format(DATA_EXTENSO),
                        evangelho.referencia(),
                        evangelho.texto()
                );
    }

    /** Pre-visualizacao usada pelo painel antes de disparar de fato. */
    public String previsualizarMensagem() {
        return formatarMensagem(liturgiaService.buscarEvangelhoDoDia());
    }
}
