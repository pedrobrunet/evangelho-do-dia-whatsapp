package com.botwpp.evangelho.repository;

import com.botwpp.evangelho.config.AppProperties;
import com.botwpp.evangelho.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a persistencia das contas em arquivo: o cadastro precisa sobreviver
 * ao reinicio da aplicacao, e o hash de senha nunca pode se perder no caminho.
 *
 * Fica no pacote do repositorio para poder chamar carregar(), que em producao
 * roda via @PostConstruct.
 */
class UsuarioRepositoryTest {

    @TempDir
    Path pasta;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UsuarioRepository repositorioEm(Path arquivo) {
        AppProperties properties = new AppProperties();
        properties.setArquivoUsuarios(arquivo.toString());

        UsuarioRepository repository = new UsuarioRepository(objectMapper, properties);
        repository.carregar();
        return repository;
    }

    private Usuario usuario(String id, String email) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNome("Maria");
        usuario.setEmail(email);
        usuario.setSenhaHash("$2a$10$hashfalsoparateste000000000000000000000000000000000000");
        usuario.setCriadoEm(LocalDateTime.of(2026, 8, 18, 10, 30));
        return usuario;
    }

    @Test
    void deveComecarVazioQuandoOArquivoAindaNaoExiste() {
        UsuarioRepository repository = repositorioEm(pasta.resolve("sub/usuarios.json"));

        assertThat(repository.total()).isZero();
        assertThat(repository.buscarPorEmail("maria@exemplo.com")).isEmpty();
    }

    @Test
    void deveGravarNoArquivoERecarregarNaProximaExecucao() {
        Path arquivo = pasta.resolve("dados/usuarios.json");
        repositorioEm(arquivo).inserir(usuario("id-1", "maria@exemplo.com"));

        // Simula o reinicio: instancia nova, lendo o mesmo arquivo.
        UsuarioRepository aposReiniciar = repositorioEm(arquivo);

        assertThat(aposReiniciar.total()).isEqualTo(1);
        assertThat(aposReiniciar.buscarPorEmail("maria@exemplo.com"))
                .get()
                .satisfies(u -> {
                    assertThat(u.getId()).isEqualTo("id-1");
                    assertThat(u.getNome()).isEqualTo("Maria");
                    assertThat(u.getSenhaHash()).startsWith("$2a$");
                    assertThat(u.getCriadoEm()).isEqualTo(LocalDateTime.of(2026, 8, 18, 10, 30));
                });
    }

    @Test
    void naoDeveGravarAInstanciaDerivadaNoArquivo() throws IOException {
        Path arquivo = pasta.resolve("usuarios.json");
        repositorioEm(arquivo).inserir(usuario("id-1", "maria@exemplo.com"));

        // A instancia deriva do id; grava-la criaria uma segunda fonte da verdade.
        assertThat(Files.readString(arquivo)).doesNotContain("instancia");
    }

    @Test
    void deveBuscarPorEmailIgnorandoCaixaEEspacos() {
        UsuarioRepository repository = repositorioEm(pasta.resolve("usuarios.json"));
        repository.inserir(usuario("id-1", "maria@exemplo.com"));

        assertThat(repository.buscarPorEmail("  MARIA@Exemplo.COM  ")).isPresent();
        assertThat(repository.buscarPorEmail(null)).isEmpty();
    }

    @Test
    void deveIsolarAsContasPeloId() {
        UsuarioRepository repository = repositorioEm(pasta.resolve("usuarios.json"));
        repository.inserir(usuario("id-1", "maria@exemplo.com"));
        repository.inserir(usuario("id-2", "joao@exemplo.com"));

        assertThat(repository.buscarPorId("id-2")).get()
                .extracting(Usuario::getEmail).isEqualTo("joao@exemplo.com");
        assertThat(repository.buscarPorId("id-3")).isEmpty();
    }

    @Test
    void deveComecarVazioQuandoOArquivoEstaCorrompido() {
        Path arquivo = pasta.resolve("usuarios.json");
        try {
            Files.writeString(arquivo, "{ isso nao e uma lista de contas");
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        // Preferimos subir sem contas a derrubar a aplicacao no @PostConstruct.
        UsuarioRepository repository = repositorioEm(arquivo);

        assertThat(repository.total()).isZero();
    }
}
