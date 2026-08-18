package com.botwpp.evangelho.repository;

import com.botwpp.evangelho.config.AppProperties;
import com.botwpp.evangelho.model.Agendamento;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia dos agendamentos em arquivo JSON.
 *
 * Arquivo em vez de banco porque o volume e pequeno e o dominio simples.
 * Se o projeto crescer, basta trocar esta classe por um JpaRepository sem
 * impactar os services.
 */
@Repository
public class AgendamentoRepository {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoRepository.class);

    private final ObjectMapper objectMapper;
    private final Path arquivo;
    private final Path arquivoLegado;

    /** Cache em memoria: o scheduler le a lista a cada minuto. */
    private final List<Agendamento> cache = new ArrayList<>();

    public AgendamentoRepository(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.arquivo = Path.of(properties.getArquivoAgendamentos());
        this.arquivoLegado = Path.of(properties.getArquivoConfiguracao());
    }

    @PostConstruct
    synchronized void carregar() {
        if (Files.exists(arquivo)) {
            try {
                cache.addAll(objectMapper.readValue(arquivo.toFile(), new TypeReference<List<Agendamento>>() {}));
                log.info("{} agendamento(s) carregado(s) de {}", cache.size(), arquivo.toAbsolutePath());
                return;
            } catch (IOException e) {
                log.error("Falha ao ler {}. Comecando sem agendamentos.", arquivo.toAbsolutePath(), e);
                return;
            }
        }

        migrarConfiguracaoAntiga();
    }

    /**
     * Converte o arquivo da versao de agendamento unico, quando presente.
     * Evita que quem ja usava o painel perca a programacao ao atualizar.
     */
    private void migrarConfiguracaoAntiga() {
        if (!Files.exists(arquivoLegado)) {
            log.info("Nenhum agendamento previo encontrado. Comecando vazio.");
            return;
        }

        try {
            JsonNode antigo = objectMapper.readTree(arquivoLegado.toFile());
            String grupoId = antigo.path("grupoId").asText("");
            if (grupoId.isBlank()) {
                return;
            }

            Agendamento agendamento = new Agendamento();
            agendamento.setId(UUID.randomUUID().toString());
            agendamento.setGrupoId(grupoId);
            agendamento.setGrupoNome(antigo.path("grupoNome").asText(""));
            agendamento.setAtivo(antigo.path("ativo").asBoolean(false));
            agendamento.setHorarioEnvio(LocalTime.parse(antigo.path("horarioEnvio").asText("08:00")));

            String ultimoEnvio = antigo.path("ultimoEnvio").asText("");
            if (!ultimoEnvio.isBlank()) {
                agendamento.setUltimoEnvio(LocalDate.parse(ultimoEnvio));
            }

            cache.add(agendamento);
            gravar();
            log.info("Configuracao antiga migrada para o novo formato de agendamentos.");

        } catch (Exception e) {
            log.warn("Nao foi possivel migrar {}: {}", arquivoLegado, e.getMessage());
        }
    }

    /** Lista ordenada por horario, como o painel exibe. */
    public synchronized List<Agendamento> listar() {
        List<Agendamento> copia = new ArrayList<>(cache);
        copia.sort(Comparator.comparing(Agendamento::getHorarioEnvio));
        return copia;
    }

    public synchronized Optional<Agendamento> buscar(String id) {
        return cache.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    /** Insere um novo agendamento, atribuindo o identificador. */
    public synchronized Agendamento inserir(Agendamento agendamento) {
        agendamento.setId(UUID.randomUUID().toString());
        cache.add(agendamento);
        gravar();
        return agendamento;
    }

    /** Persiste alteracoes feitas em um agendamento ja existente. */
    public synchronized void atualizar() {
        gravar();
    }

    public synchronized boolean remover(String id) {
        boolean removido = cache.removeIf(a -> a.getId().equals(id));
        if (removido) {
            gravar();
        }
        return removido;
    }

    private void gravar() {
        try {
            if (arquivo.getParent() != null) {
                Files.createDirectories(arquivo.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(arquivo.toFile(), cache);
        } catch (IOException e) {
            // Nao propaga: perder a persistencia nao deve derrubar o envio em andamento.
            log.error("Falha ao gravar os agendamentos em {}", arquivo.toAbsolutePath(), e);
        }
    }
}
