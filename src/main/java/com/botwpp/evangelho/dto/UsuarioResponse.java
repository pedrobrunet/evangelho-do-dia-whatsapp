package com.botwpp.evangelho.dto;

import com.botwpp.evangelho.model.Usuario;

/**
 * Representacao publica do usuario. Existe para que o hash de senha,
 * presente no modelo persistido, jamais transite pela API.
 */
public record UsuarioResponse(String id, String nome, String email) {

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
