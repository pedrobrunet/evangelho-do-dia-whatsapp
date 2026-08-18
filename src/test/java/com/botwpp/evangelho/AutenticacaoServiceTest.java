package com.botwpp.evangelho;

import com.botwpp.evangelho.config.EmailProperties;
import com.botwpp.evangelho.dto.LoginRequest;
import com.botwpp.evangelho.dto.RegistroRequest;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.repository.UsuarioRepository;
import com.botwpp.evangelho.service.AutenticacaoService;
import com.botwpp.evangelho.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre cadastro e login: guarda da senha, isolamento por e-mail e as
 * garantias de privacidade das mensagens de erro.
 */
class AutenticacaoServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 8, 18, 10, 30);
    private static final String SENHA = "senha-secreta-123";

    private UsuarioRepository repository;
    private EmailService emailService;
    private AutenticacaoService service;

    @BeforeEach
    void preparar() {
        repository = mock(UsuarioRepository.class);
        emailService = mock(EmailService.class);
        when(repository.buscarPorEmail(anyString())).thenReturn(Optional.empty());
        when(repository.inserir(any(Usuario.class))).thenAnswer(chamada -> chamada.getArgument(0));

        Clock clock = Clock.fixed(AGORA.atZone(SP).toInstant(), SP);
        service = new AutenticacaoService(repository, emailService, new EmailProperties(), clock);
    }

    /** Guarda a conta que foi entregue ao repositorio. */
    private Usuario inserido() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).inserir(captor.capture());
        return captor.getValue();
    }

    private Usuario cadastrado(String email, String senha) {
        Usuario usuario = service.registrar(new RegistroRequest("Maria", email, senha));
        when(repository.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        return usuario;
    }

    @Test
    void deveGuardarApenasOHashDaSenha() {
        service.registrar(new RegistroRequest("Maria", "maria@exemplo.com", SENHA));

        Usuario salvo = inserido();
        assertThat(salvo.getSenhaHash()).isNotEqualTo(SENHA);
        assertThat(salvo.getSenhaHash()).startsWith("$2a$");
    }

    @Test
    void deveNormalizarOEmailERemoverEspacos() {
        service.registrar(new RegistroRequest("  Maria  ", "  Maria@Exemplo.COM ", SENHA));

        Usuario salvo = inserido();
        assertThat(salvo.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(salvo.getNome()).isEqualTo("Maria");
        assertThat(salvo.getCriadoEm()).isEqualTo(AGORA);
    }

    @Test
    void deveDerivarUmaInstanciaDeWhatsappPropriaDaConta() {
        service.registrar(new RegistroRequest("Maria", "maria@exemplo.com", SENHA));

        Usuario salvo = inserido();
        assertThat(salvo.getInstancia()).isEqualTo("u" + salvo.getId().replace("-", ""));
        assertThat(salvo.getInstancia()).doesNotContain("-");
    }

    @Test
    void deveRecusarCadastroComEmailJaUsadoMesmoEmOutraCaixa() {
        Usuario existente = new Usuario();
        existente.setId("id-1");
        existente.setEmail("maria@exemplo.com");
        when(repository.buscarPorEmail("maria@exemplo.com")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.registrar(
                new RegistroRequest("Maria", "MARIA@exemplo.com", SENHA)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ja existe uma conta");

        verify(repository, never()).inserir(any(Usuario.class));
    }

    @Test
    void deveConcluirOCadastroMesmoQuandoOEmailDeBoasVindasFalha() {
        doThrow(new RuntimeException("provedor fora do ar"))
                .when(emailService).enviarBoasVindas(any(Usuario.class), anyString());

        Usuario usuario = service.registrar(new RegistroRequest("Maria", "maria@exemplo.com", SENHA));

        assertThat(usuario.getId()).isNotBlank();
        verify(repository).inserir(any(Usuario.class));
    }

    @Test
    void deveAutenticarComASenhaCorreta() {
        Usuario cadastrado = cadastrado("maria@exemplo.com", SENHA);

        Usuario logado = service.autenticar(new LoginRequest(" Maria@Exemplo.com ", SENHA));

        assertThat(logado.getId()).isEqualTo(cadastrado.getId());
    }

    @Test
    void deveRejeitarSenhaErradaComAMesmaMensagemDeEmailInexistente() {
        cadastrado("maria@exemplo.com", SENHA);

        String erroSenhaErrada = capturarErro("maria@exemplo.com", "outra-senha-qualquer");
        String erroEmailInexistente = capturarErro("ninguem@exemplo.com", SENHA);

        // Mensagens diferentes revelariam quais e-mails tem conta cadastrada.
        assertThat(erroSenhaErrada).isEqualTo(erroEmailInexistente);
    }

    private String capturarErro(String email, String senha) {
        try {
            service.autenticar(new LoginRequest(email, senha));
            throw new AssertionError("Esperava a recusa do login para " + email);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }

    @Test
    void deveEncontrarAContaPeloId() {
        Usuario usuario = new Usuario();
        usuario.setId("id-1");
        when(repository.buscarPorId("id-1")).thenReturn(Optional.of(usuario));

        assertThat(service.buscarPorId("id-1")).contains(usuario);
        assertThat(service.buscarPorId("id-2")).isEmpty();
    }
}
