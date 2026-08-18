package com.botwpp.evangelho.model;

/**
 * Grupo de WhatsApp disponivel na conta conectada.
 * Alimenta o seletor de destino no painel, evitando que o usuario
 * precise descobrir e digitar o ID manualmente.
 *
 * @param id   identificador no formato 120363XXXXXXXXXX@g.us
 * @param nome nome exibido do grupo
 * @param participantes quantidade de membros, quando informada pela API
 */
public record Grupo(String id, String nome, int participantes) {
}
