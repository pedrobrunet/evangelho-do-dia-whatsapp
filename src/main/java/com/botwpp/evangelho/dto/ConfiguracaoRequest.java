package com.botwpp.evangelho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Payload aceito pelo endpoint PUT /api/configuracao.
 * O horario chega como string "HH:mm" — formato nativo do input type="time" do HTML.
 */
public record ConfiguracaoRequest(

        @NotBlank(message = "Informe o horario de envio.")
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Horario deve estar no formato HH:mm.")
        String horarioEnvio,

        /** Aceita grupo (…@g.us) ou numero internacional. Validado tambem no service. */
        @NotBlank(message = "Informe o ID do grupo ou numero de destino.")
        @Pattern(regexp = "^[0-9A-Za-z@._-]{6,80}$",
                message = "Destino invalido. Use apenas numeros, letras, @, ponto, hifen ou underline.")
        String grupoId,

        boolean ativo
) {
}
