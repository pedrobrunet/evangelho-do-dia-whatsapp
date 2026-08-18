package com.botwpp.evangelho.dto;

/**
 * Envelope padrao de resposta da API, consumido pelo frontend
 * para renderizar o feedback visual (sucesso/erro).
 */
public record RespostaApi(boolean sucesso, String mensagem, Object dados) {

    public static RespostaApi ok(String mensagem) {
        return new RespostaApi(true, mensagem, null);
    }

    public static RespostaApi ok(String mensagem, Object dados) {
        return new RespostaApi(true, mensagem, dados);
    }

    public static RespostaApi erro(String mensagem) {
        return new RespostaApi(false, mensagem, null);
    }
}
