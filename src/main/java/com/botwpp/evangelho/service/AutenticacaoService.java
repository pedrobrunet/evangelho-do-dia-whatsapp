package com.botwpp.evangelho.service;

import com.botwpp.evangelho.config.EmailProperties;
import com.botwpp.evangelho.dto.LoginRequest;
import com.botwpp.evangelho.dto.RegistroRequest;
import com.botwpp.evangelho.model.Usuario;
import com.botwpp.evangelho.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cadastro e autenticacao das contas.
 */
@Service
public class AutenticacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoService.class);

    /**
     * Hash usado quando o e-mail nao existe, para que a verificacao de senha
     * gaste o mesmo tempo de um login valido. Sem isso, a diferenca de tempo
     * revelaria quais e-mails estao cadastrados.
     */
    private static final String HASH_FICTICIO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UsuarioRepository repository;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Clock clock;

    public AutenticacaoService(UsuarioRepository repository,
                               EmailService emailService,
                               EmailProperties emailProperties,
                               Clock clock) {
        this.repository = repository;
        this.emailService = emailService;
        this.emailProperties = emailProperties;
        this.clock = clock;
    }

    /**
     * Cria a conta e dispara o e-mail de boas-vindas.
     *
     * @throws IllegalArgumentException se o e-mail ja estiver em uso
     */
    public Usuario registrar(RegistroRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (repository.buscarPorEmail(email).isPresent()) {
            throw new IllegalArgumentException("Ja existe uma conta com este e-mail.");
        }

        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setNome(request.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(encoder.encode(request.senha()));
        usuario.setCriadoEm(LocalDateTime.now(clock));

        repository.inserir(usuario);
        log.info("Conta criada para {} (instancia {})", mascarar(email), usuario.getInstancia());

        // Fora do caminho critico: o cadastro ja esta concluido e persistido.
        // Uma falha de e-mail nao pode impedir o acesso a conta recem-criada.
        try {
            emailService.enviarBoasVindas(usuario, emailProperties.getUrlPainel());
        } catch (RuntimeException e) {
            log.error("Conta criada, porem o e-mail de boas-vindas falhou.", e);
        }

        return usuario;
    }

    /**
     * Valida as credenciais.
     *
     * A mensagem de erro e identica para e-mail inexistente e senha errada:
     * distingui-las permitiria descobrir quais e-mails tem conta.
     */
    public Usuario autenticar(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Optional<Usuario> encontrado = repository.buscarPorEmail(email);

        String hash = encontrado.map(Usuario::getSenhaHash).orElse(HASH_FICTICIO);
        boolean senhaConfere = encoder.matches(request.senha(), hash);

        if (encontrado.isEmpty() || !senhaConfere) {
            log.info("Tentativa de login rejeitada para {}", mascarar(email));
            throw new IllegalArgumentException("E-mail ou senha invalidos.");
        }

        log.info("Login efetuado por {}", mascarar(email));
        return encontrado.get();
    }

    public Optional<Usuario> buscarPorId(String id) {
        return repository.buscarPorId(id);
    }

    private String mascarar(String email) {
        int arroba = email.indexOf('@');
        return arroba <= 1 ? "***" : email.charAt(0) + "***" + email.substring(arroba);
    }
}
