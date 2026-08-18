package com.botwpp.evangelho.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Configuracao editavel pelo usuario atraves do frontend.
 * E persistida em disco (ver ConfiguracaoRepository) para sobreviver a reinicios.
 */
public class ConfiguracaoEnvio {

    /** Horario diario do disparo, no fuso configurado em app.timezone. */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    private LocalTime horarioEnvio = LocalTime.of(8, 0);

    /** ID do grupo (ex.: 120363XXXXXXXXXX@g.us) ou numero no formato 5511999999999. */
    private String grupoId = "";

    /** Nome do grupo escolhido, guardado apenas para exibicao no painel. */
    private String grupoNome = "";

    /** Chave geral: quando false, o scheduler nao dispara nada. */
    private boolean ativo = false;

    /** Ultima data efetivamente enviada — evita envio duplicado no mesmo dia. */
    private LocalDate ultimoEnvio;

    /** Resultado do ultimo disparo, exibido no frontend. */
    private String ultimoStatus = "Nenhum envio realizado ainda.";

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
}
