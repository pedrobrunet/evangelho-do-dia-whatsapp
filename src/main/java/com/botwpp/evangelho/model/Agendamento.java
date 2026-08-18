package com.botwpp.evangelho.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Um envio programado: um grupo, um horario diario.
 *
 * A aplicacao mantem uma lista destes — o scheduler percorre todos os ativos
 * a cada minuto e dispara os que chegaram na hora.
 */
public class Agendamento {

    /** Identificador estavel, usado pelo painel para editar e remover. */
    private String id;

    /** Dono do agendamento. Toda leitura e escrita e filtrada por ele. */
    private String usuarioId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horarioEnvio = LocalTime.of(8, 0);

    /** ID do grupo (…@g.us) ou numero no formato internacional. */
    private String grupoId = "";

    /** Nome do grupo, guardado para exibicao no painel. */
    private String grupoNome = "";

    /** Quando false, o scheduler ignora este agendamento. */
    private boolean ativo = true;

    /** Ultima data efetivamente enviada — garante um envio por dia. */
    private LocalDate ultimoEnvio;

    /** Resultado do ultimo disparo deste agendamento. */
    private String ultimoStatus = "Nenhum envio realizado ainda.";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public LocalTime getHorarioEnvio() {
        return horarioEnvio;
    }

    public void setHorarioEnvio(LocalTime horarioEnvio) {
        this.horarioEnvio = horarioEnvio;
    }

    public String getGrupoId() {
        return grupoId;
    }

    public void setGrupoId(String grupoId) {
        this.grupoId = grupoId;
    }

    public String getGrupoNome() {
        return grupoNome;
    }

    public void setGrupoNome(String grupoNome) {
        this.grupoNome = grupoNome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDate getUltimoEnvio() {
        return ultimoEnvio;
    }

    public void setUltimoEnvio(LocalDate ultimoEnvio) {
        this.ultimoEnvio = ultimoEnvio;
    }

    public String getUltimoStatus() {
        return ultimoStatus;
    }

    public void setUltimoStatus(String ultimoStatus) {
        this.ultimoStatus = ultimoStatus;
    }

    /** Rotulo amigavel para logs e mensagens de erro. */
    public String descricao() {
        String nome = (grupoNome == null || grupoNome.isBlank()) ? grupoId : grupoNome;
        return horarioEnvio + " → " + nome;
    }
}
