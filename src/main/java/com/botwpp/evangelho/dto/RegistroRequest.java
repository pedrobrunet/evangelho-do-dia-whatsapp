package com.botwpp.evangelho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Payload do cadastro de uma nova conta.
 */
public record RegistroRequest(

        @NotBlank(message = "Informe seu nome.")
        @Size(min = 2, max = 80, message = "O nome deve ter entre 2 e 80 caracteres.")
        String nome,

        @NotBlank(message = "Informe seu e-mail.")
        @Email(message = "E-mail invalido.")
        @Size(max = 160, message = "E-mail muito longo.")
        String email,

        // O limite superior evita o ataque de negacao de servico por hash de
        // senhas muito longas: o custo do BCrypt cresce com o tamanho da entrada.
        @NotBlank(message = "Informe uma senha.")
        @Size(min = 8, max = 100, message = "A senha deve ter entre 8 e 100 caracteres.")
        String senha
) {

    /**
     * Normaliza nome e e-mail antes da validacao — o Bean Validation roda
     * sobre o record ja construido, entao um e-mail colado com espacos em
     * volta seria recusado por @Email se chegasse cru ate aqui.
     *
     * A senha nao passa por trim: o espaco pode ser parte dela.
     */
    public RegistroRequest {
        nome = nome == null ? null : nome.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
