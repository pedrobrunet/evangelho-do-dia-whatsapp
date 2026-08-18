package com.botwpp.evangelho.repository;

import com.botwpp.evangelho.config.AppProperties;
import com.botwpp.evangelho.model.ConfiguracaoEnvio;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistencia simples da configuracao em arquivo JSON.
 *
 * Optou-se por arquivo (e nao banco) porque o dominio e um unico registro
 * de configuracao. Caso o projeto cresca, basta trocar esta classe por um
 * JpaRepository sem impactar os services.
 */
@Repository
public class ConfiguracaoRepository {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoRepository.class);

    private final ObjectMapper objectMapper;
    private final Path arquivo;

    /** Cache em memoria: o scheduler le a configuracao a cada minuto. */
    private ConfiguracaoEnvio cache = new ConfiguracaoEnvio();

    public ConfiguracaoRepository(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.arquivo = Path.of(properties.getArquivoConfiguracao());
    }

    /** Carrega o arquivo na subida da aplicacao; se nao existir, mantem os defaults. */
    @PostConstruct
    void carregar() {
        if (!Files.exists(arquivo)) {
            log.info("Nenhuma configuracao previa encontrada em {}. Usando valores padrao.", arquivo.toAbsolutePath());
            return;
        }
        try {
            cache = objectMapper.readValue(arquivo.toFile(), ConfiguracaoEnvio.class);
            log.info("Configuracao carregada de {}", arquivo.toAbsolutePath());
        } catch (IOException e) {
            log.error("Falha ao ler {}. Usando valores padrao.", arquivo.toAbsolutePath(), e);
        }
    }

    public synchronized ConfiguracaoEnvio buscar() {
        return cache;
    }

    /** Atualiza o cache e grava em disco. */
    public synchronized void salvar(ConfiguracaoEnvio configuracao) {
        this.cache = configuracao;
        try {
            if (arquivo.getParent() != null) {
                Files.createDirectories(arquivo.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(arquivo.toFile(), configuracao);
        } catch (IOException e) {
            // Nao propaga: perder a persistencia nao deve derrubar o envio em andamento.
            log.error("Falha ao gravar a configuracao em {}", arquivo.toAbsolutePath(), e);
        }
    }
}
