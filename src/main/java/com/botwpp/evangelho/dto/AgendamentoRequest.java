package com.botwpp.evangelho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload de criacao e edicao de um agendamento.
 * O grupo vem do seletor alimentado pela conexao ativa — o usuario
 * nao digita identificadores manualmente.
 */
public record AgendamentoRequest(

        @NotBlank(message = "Informe o horario de envio.")
        @Pattern(regexp = "^([01][0-9]|2[0-3]):[0-5][0-9]$", message = "Horario deve estar no formato HH:mm.")
        String horarioEnvio,

        @NotBlank(message = "Selecione o grupo de destino.")
        @Pattern(regexp = "^[0-9A-Za-z@._-]{6,80}$",
                message = "Destino invalido. Use apenas numeros, letras, @, ponto, hifen ou underline.")
        String grupoId,

        @Size(max = 120, message = "Nome do grupo muito longo.")
        String grupoNome,

        boolean ativo
) {
}
