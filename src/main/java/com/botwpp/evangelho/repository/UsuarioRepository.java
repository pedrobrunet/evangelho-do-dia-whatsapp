package com.botwpp.evangelho.repository;

import com.botwpp.evangelho.config.AppProperties;
import com.botwpp.evangelho.model.Usuario;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Persistencia das contas em arquivo JSON, no mesmo esquema dos agendamentos.
 *
 * O arquivo contem hashes de senha: deve ficar fora do versionamento
 * (ja coberto por data/ no .gitignore) e dentro do volume persistente.
 */
@Repository
public class UsuarioRepository {

    private static final Logger log = LoggerFactory.getLogger(UsuarioRepository.class);

    private final ObjectMapper objectMapper;
    private final Path arquivo;
    private final List<Usuario> cache = new ArrayList<>();

    public UsuarioRepository(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.arquivo = Path.of(properties.getArquivoUsuarios());
    }

    @PostConstruct
    synchronized void carregar() {
        if (!Files.exists(arquivo)) {
            log.info("Nenhuma conta cadastrada ainda em {}", arquivo.toAbsolutePath());
            return;
        }
        try {
            cache.addAll(objectMapper.readValue(arquivo.toFile(), new TypeReference<List<Usuario>>() {}));
            log.info("{} conta(s) carregada(s).", cache.size());
        } catch (IOException e) {
            log.error("Falha ao ler {}. Comecando sem contas.", arquivo.toAbsolutePath(), e);
        }
    }

    /** Busca por e-mail, normalizando para minusculas. */
    public synchronized Optional<Usuario> buscarPorEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String alvo = email.trim().toLowerCase(Locale.ROOT);
        return cache.stream().filter(u -> alvo.equals(u.getEmail())).findFirst();
    }

    public synchronized Optional<Usuario> buscarPorId(String id) {
        return cache.stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    public synchronized Usuario inserir(Usuario usuario) {
        cache.add(usuario);
        gravar();
        return usuario;
    }

    public synchronized int total() {
        return cache.size();
    }

    private void gravar() {
        try {
            if (arquivo.getParent() != null) {
                Files.createDirectories(arquivo.getParent());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(arquivo.toFile(), cache);
        } catch (IOException e) {
            log.error("Falha ao gravar as contas em {}", arquivo.toAbsolutePath(), e);
        }
    }
}
