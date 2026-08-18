package com.botwpp.evangelho.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * Conta de acesso ao painel.
 *
 * Cada usuario tem a propria sessao de WhatsApp e os proprios agendamentos —
 * um nunca enxerga os do outro.
 *
 * Este objeto e persistido integralmente em disco, hash de senha incluso, e
 * por isso NUNCA deve ser devolvido pela API: os controllers respondem com
 * UsuarioResponse, que carrega apenas os campos publicos.
 */
public class Usuario {

    private String id;
    private String nome;

    /** Guardado sempre em minusculas: e a chave de login e precisa ser estavel. */
    private String email;

    /** Hash BCrypt da senha — a senha em texto puro nunca e armazenada. */
    private String senhaHash;

    private LocalDateTime criadoEm;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    /**
     * Nome da instancia deste usuario no bridge/Evolution API.
     *
     * Deriva do id para ser estavel e unico, e sai sem hifens porque compoe
     * caminhos de URL nas chamadas ao bridge. Derivado, entao nao vai ao arquivo.
     */
    @JsonIgnore
    public String getInstancia() {
        return "u" + id.replace("-", "");
    }
}
