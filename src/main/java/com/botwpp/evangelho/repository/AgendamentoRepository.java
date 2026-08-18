package com.botwpp.evangelho.repository;

import com.botwpp.evangelho.config.AppProperties;
import com.botwpp.evangelho.model.Agendamento;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia dos agendamentos em arquivo JSON.
 *
 * Todo acesso vindo do painel e filtrado pelo dono: um id de agendamento que
 * vaze para outra conta nao pode ser lido, editado nem removido por ela.
 * A unica leitura sem filtro e listarTodos(), usada pelo scheduler.
 */
@Repository
public class AgendamentoRepository {

    private static final Logger log = LoggerFactory.getLogger(AgendamentoRepository.class);

    private final ObjectMapper objectMapper;
    private final Path arquivo;

    /** Cache em memoria: o scheduler le a lista a cada minuto. */
    private final List<Agendamento> cache = new ArrayList<>();

    public AgendamentoRepository(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.arquivo = Path.of(properties.getArquivoAgendamentos());
    }

    @PostConstruct
    synchronized void carregar() {
        if (!Files.exists(arquivo)) {
            log.info("Nenhum agendamento previo encontrado. Comecando vazio.");
            return;
        }
        try {
            cache.addAll(objectMapper.readValue(arquivo.toFile(), new TypeReference<List<Agendamento>>() {}));
            log.info("{} agendamento(s) carregado(s) de {}", cache.size(), arquivo.toAbsolutePath());
        } catch (IOException e) {
            log.error("Falha ao ler {}. Comecando sem agendamentos.", arquivo.toAbsolutePath(), e);
        }
    }

    /** Todos os agendamentos, de todas as contas. Uso exclusivo do scheduler. */
    public synchronized List<Agendamento> listarTodos() {
        return new ArrayList<>(cache);
    }

    /** Agendamentos de uma conta, ordenados por horario — o que o painel exibe. */
    public synchronized List<Agendamento> listarDoUsuario(String usuarioId) {
        List<Agendamento> copia = new ArrayList<>();
        for (Agendamento agendamento : cache) {
            if (usuarioId.equals(agendamento.getUsuarioId())) {
                copia.add(agendamento);
            }
        }
        copia.sort(Comparator.comparing(Agendamento::getHorarioEnvio));
        return copia;
    }

    /** Busca dentro da conta informada; ignora agendamentos de outros donos. */
    public synchronized Optional<Agendamento> buscar(String usuarioId, String id) {
        return cache.stream()
                .filter(a -> a.getId().equals(id) && usuarioId.equals(a.getUsuarioId()))
                .findFirst();
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

    public synchronized boolean remover(String usuarioId, String id) {
        boolean removido = cache.removeIf(
                a -> a.getId().equals(id) && usuarioId.equals(a.getUsuarioId()));
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
