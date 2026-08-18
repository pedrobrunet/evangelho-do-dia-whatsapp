package com.botwpp.evangelho.controller;

import com.botwpp.evangelho.dto.RespostaApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Traduz excecoes em respostas JSON previsiveis para o frontend.
 *
 * Regra de seguranca: nenhuma stack trace vai para o cliente — apenas
 * uma mensagem curta. O detalhe fica no log do servidor.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    /** Erros de validacao do @Valid (horario/destino invalidos). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespostaApi> validacao(MethodArgumentNotValidException e) {
        String mensagem = e.getBindingResult().getFieldErrors().stream()
                .map(erro -> erro.getDefaultMessage())
                .collect(Collectors.joining(" "));
        return ResponseEntity.badRequest().body(RespostaApi.erro(mensagem));
    }

    /** Regras de negocio: destino ausente, fonte indisponivel, falha no envio. */
    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<RespostaApi> negocio(RuntimeException e) {
        log.warn("Requisicao rejeitada: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(RespostaApi.erro(e.getMessage()));
    }

    /** Rede de seguranca para qualquer outra falha inesperada. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespostaApi> inesperado(Exception e) {
        log.error("Erro inesperado ao processar a requisicao.", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RespostaApi.erro("Erro interno. Consulte os logs da aplicacao."));
    }
}
