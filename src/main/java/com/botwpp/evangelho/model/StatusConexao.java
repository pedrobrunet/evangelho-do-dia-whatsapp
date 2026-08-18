package com.botwpp.evangelho.model;

/**
 * Estado da conexao com o WhatsApp, exibido no painel.
 *
 * @param estado      CONECTADO, AGUARDANDO_LEITURA, DESCONECTADO ou INDISPONIVEL
 * @param qrCodeBase64 imagem do QR code em data URI, presente apenas enquanto aguarda leitura
 * @param codigoPareamento codigo de 8 caracteres para conectar sem camera
 * @param descricao   texto pronto para a interface
 */
public record StatusConexao(
        Estado estado,
        String qrCodeBase64,
        String codigoPareamento,
        String descricao
) {

    public enum Estado {
        /** Sessao ativa: pode enviar mensagens. */
        CONECTADO,
        /** QR code ou codigo gerado, esperando o celular parear. */
        AGUARDANDO_LEITURA,
        /** Instancia existe mas nao esta pareada. */
        DESCONECTADO,
        /** Evolution API fora do ar ou mal configurada. */
        INDISPONIVEL
    }

    public static StatusConexao conectado() {
        return new StatusConexao(Estado.CONECTADO, null, null,
                "WhatsApp conectado e pronto para enviar.");
    }

    public static StatusConexao aguardando(String qrCodeBase64, String codigoPareamento) {
        return new StatusConexao(Estado.AGUARDANDO_LEITURA, qrCodeBase64, codigoPareamento,
                "Leia o QR code no celular ou use o codigo de pareamento.");
    }

    public static StatusConexao desconectado() {
        return new StatusConexao(Estado.DESCONECTADO, null, null,
                "Nenhum WhatsApp conectado.");
    }

    public static StatusConexao indisponivel(String motivo) {
        return new StatusConexao(Estado.INDISPONIVEL, null, null, motivo);
    }

    public boolean conectadoComSucesso() {
        return estado == Estado.CONECTADO;
    }
}
