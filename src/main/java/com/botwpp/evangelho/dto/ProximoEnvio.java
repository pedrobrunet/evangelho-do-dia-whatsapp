package com.botwpp.evangelho.dto;

import java.time.LocalDateTime;

/**
 * Uma entrada da fila de proximos envios.
 *
 * O instante vai como data/hora absoluta para que o painel calcule a contagem
 * regressiva no cliente, sem depender de um novo request a cada segundo.
 *
 * @param agendamentoId id do agendamento que originou este disparo
 * @param grupoNome     nome do grupo de destino
 * @param quando        instante do proximo disparo, no fuso da aplicacao
 * @param emMinutos     minutos restantes no momento da consulta
 * @param hoje          true quando o disparo ocorre ainda no dia corrente
 */
public record ProximoEnvio(
        String agendamentoId,
        String grupoNome,
        LocalDateTime quando,
        long emMinutos,
        boolean hoje
) {
}
