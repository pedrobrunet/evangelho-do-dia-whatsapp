package com.botwpp.evangelho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload do envio avulso: dispara o Evangelho de hoje para um grupo
 * sem depender de um agendamento cadastrado.
 */
public record EnvioManualRequest(

        @NotBlank(message = "Selecione o grupo de destino.")
        @Pattern(regexp = "^[0-9A-Za-z@._-]{6,80}$",
                message = "Destino invalido. Use apenas numeros, letras, @, ponto, hifen ou underline.")
        String grupoId
) {
}
