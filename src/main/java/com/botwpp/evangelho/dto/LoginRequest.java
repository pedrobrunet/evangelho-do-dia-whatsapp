package com.botwpp.evangelho.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload do login. Sem validacao de formato: a mensagem de erro para
 * credencial invalida e sempre a mesma, para nao revelar quais e-mails existem.
 */
public record LoginRequest(

        @NotBlank(message = "Informe o e-mail.")
        String email,

        @NotBlank(message = "Informe a senha.")
        String senha
) {
}
