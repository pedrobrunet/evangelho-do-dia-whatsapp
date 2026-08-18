package com.botwpp.evangelho;

import com.botwpp.evangelho.dto.RegistroRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre a validacao do cadastro.
 *
 * A normalizacao precisa acontecer antes da validacao: o Bean Validation roda
 * sobre o objeto ja construido, entao um e-mail colado com espacos em volta
 * seria recusado como invalido se o record guardasse o texto cru.
 */
class RegistroRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void abrir() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void fechar() {
        factory.close();
    }

    @Test
    void deveAceitarEmailColadoComEspacosEmVolta() {
        RegistroRequest request = new RegistroRequest(
                "  Maria  ", "  Maria@Exemplo.COM  ", "senha-secreta-123");

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.email()).isEqualTo("maria@exemplo.com");
        assertThat(request.nome()).isEqualTo("Maria");
    }

    @Test
    void deveRecusarEmailRealmenteInvalido() {
        RegistroRequest request = new RegistroRequest("Maria", "nao-e-email", "senha-secreta-123");

        assertThat(validator.validate(request))
                .extracting(v -> v.getMessage())
                .contains("E-mail invalido.");
    }

    @Test
    void deveRecusarSenhaCurta() {
        RegistroRequest request = new RegistroRequest("Maria", "maria@exemplo.com", "1234567");

        assertThat(validator.validate(request))
                .extracting(v -> v.getMessage())
                .contains("A senha deve ter entre 8 e 100 caracteres.");
    }

    @Test
    void naoDeveQuebrarComCamposAusentes() {
        RegistroRequest request = new RegistroRequest(null, null, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void naoDeveAparaASenha() {
        // Espaco pode fazer parte da senha escolhida; apara-lo mudaria a
        // credencial pelas costas do usuario.
        RegistroRequest request = new RegistroRequest("Maria", "maria@exemplo.com", " com espaco ");

        assertThat(request.senha()).isEqualTo(" com espaco ");
    }
}
