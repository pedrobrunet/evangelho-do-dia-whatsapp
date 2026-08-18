package com.botwpp.evangelho.model;

import java.time.LocalDate;

/**
 * Representa o Evangelho de um dia especifico, ja normalizado
 * independentemente da fonte (scraping ou API publica).
 *
 * @param data       dia liturgico a que o texto se refere
 * @param referencia citacao biblica (ex.: "Mt 5,1-12")
 * @param titulo     titulo da perícope (ex.: "As bem-aventurancas")
 * @param texto      corpo do Evangelho, em texto puro
 * @param fonte      origem do conteudo, util para diagnostico
 */
public record Evangelho(
        LocalDate data,
        String referencia,
        String titulo,
        String texto,
        String fonte
) {
}
