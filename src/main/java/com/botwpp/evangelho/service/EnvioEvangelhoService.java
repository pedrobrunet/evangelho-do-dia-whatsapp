package com.botwpp.evangelho.service;

import com.botwpp.evangelho.model.ConfiguracaoEnvio;
import com.botwpp.evangelho.model.Evangelho;
import com.botwpp.evangelho.repository.ConfiguracaoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Orquestra o caso de uso "enviar o Evangelho do dia":
 * busca o texto, formata a mensagem e delega o envio.
 *
 * Fica entre o scheduler e os services de infraestrutura para que
 * o disparo manual (botao "Enviar agora") e o automatico compartilhem
 * exatamente a mesma regra.
 */
@Service
public class EnvioEvangelhoService {

    private static final Logger log = LoggerFactory.getLogger(EnvioEvangelhoService.class);

    private static final DateTimeFormatter DATA_EXTENSO =
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", new Locale("pt", "BR"));

    private final LiturgiaService liturgiaService;
    private final WhatsappService whatsappService;
    private final ConfiguracaoRepository repository;
    private final Clock clock;

    public EnvioEvangelhoService(LiturgiaService liturgiaService,
                                 WhatsappService whatsappService,
                                 ConfiguracaoRepository repository,
                                 Clock clock) {
        this.liturgiaService = liturgiaService;
        this.whatsappService = whatsappService;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Executa o envio para o grupo configurado e registra o resultado.
     *
     * @return mensagem de status legivel para o frontend
     * @throws IllegalStateException se a busca ou o envio falharem
     */
    public String enviarParaGrupoConfigurado() {
        ConfiguracaoEnvio configuracao = repository.buscar();

        if (configuracao.getGrupoId() == null || configuracao.getGrupoId().isBlank()) {
            throw new IllegalStateException("Nenhum grupo/numero de destino configurado.");
        }

        Evangelho evangelho = liturgiaService.buscarEvangelhoDoDia();
        String mensagem = formatarMensagem(evangelho);

        try {
            whatsappService.enviarMensagem(configuracao.getGrupoId(), mensagem);

            configuracao.setUltimoEnvio(LocalDate.now(clock));
            String status = "Enviado com sucesso em "
                    + LocalDate.now(clock).format(DATA_EXTENSO) + " (" + evangelho.referencia() + ").";
            configuracao.setUltimoStatus(status);
            repository.salvar(configuracao);

            log.info("Envio concluido: {}", status);
            return status;

        } catch (RuntimeException e) {
            // Registra a falha sem marcar ultimoEnvio: o scheduler tentara de novo no proximo minuto.
            configuracao.setUltimoStatus("Falha no ultimo envio: " + e.getMessage());
            repository.salvar(configuracao);
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

    /** Pre-visualizacao usada pelo frontend antes de disparar de fato. */
    public String previsualizarMensagem() {
        return formatarMensagem(liturgiaService.buscarEvangelhoDoDia());
    }
}
