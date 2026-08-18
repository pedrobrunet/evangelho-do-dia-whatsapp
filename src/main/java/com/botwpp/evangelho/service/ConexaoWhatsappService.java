package com.botwpp.evangelho.service;

import com.botwpp.evangelho.model.Grupo;
import com.botwpp.evangelho.model.StatusConexao;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gerencia o ciclo de vida da conexao com o WhatsApp.
 *
 * O pareamento em si e feito pela Evolution API (que implementa o protocolo do
 * WhatsApp Web); este service cuida de criar a instancia, obter o QR code /
 * codigo de pareamento, acompanhar o estado e listar os grupos disponiveis.
 * Assim o usuario do painel nunca precisa acessar a Evolution API diretamente.
 */
@Service
public class ConexaoWhatsappService {

    private static final Logger log = LoggerFactory.getLogger(ConexaoWhatsappService.class);

    /** Estado retornado pela Evolution API quando a sessao esta ativa. */
    private static final String ESTADO_ATIVO = "open";

    private final EvolutionApiClient client;

    public ConexaoWhatsappService(EvolutionApiClient client) {
        this.client = client;
    }

    // ------------------------------------------------------------------
    // Consulta de estado
    // ------------------------------------------------------------------

    /**
     * Estado atual da conexao. Chamado em polling pelo frontend enquanto
     * o usuario nao conclui o pareamento.
     */
    public StatusConexao consultarStatus() {
        if (client.estaSimulando()) {
            return new StatusConexao(StatusConexao.Estado.CONECTADO, null, null,
                    "Modo simulacao: nenhuma conexao real com o WhatsApp.");
        }

        try {
            JsonNode resposta = client.chamar(HttpMethod.GET,
                    "/instance/connectionState/" + client.instancia(), null);

            String estado = extrairEstado(resposta);
            log.debug("Estado da instancia {}: {}", client.instancia(), estado);

            if (ESTADO_ATIVO.equalsIgnoreCase(estado)) {
                return StatusConexao.conectado();
            }
            return StatusConexao.desconectado();

        } catch (IllegalStateException e) {
            // Instancia inexistente e um caso normal no primeiro acesso.
            log.debug("Nao foi possivel consultar o estado: {}", e.getMessage());
            return StatusConexao.indisponivel(e.getMessage());
        }
    }

    /** A Evolution API responde {"instance": {"state": "open"}} ou {"state": "open"}. */
    private String extrairEstado(JsonNode resposta) {
        if (resposta == null) {
            return "";
        }
        JsonNode instancia = resposta.path("instance");
        if (!instancia.isMissingNode() && instancia.has("state")) {
            return instancia.path("state").asText("");
        }
        return resposta.path("state").asText("");
    }

    // ------------------------------------------------------------------
    // Pareamento
    // ------------------------------------------------------------------

    /**
     * Inicia o pareamento e devolve o material que o usuario precisa para conectar.
     *
     * @param numero telefone com DDI apenas com digitos (ex.: 5511999999999) para
     *               receber um codigo de pareamento; nulo/vazio gera QR code
     */
    public StatusConexao iniciarConexao(String numero) {
        if (client.estaSimulando()) {
            return new StatusConexao(StatusConexao.Estado.CONECTADO, null, null,
                    "Modo simulacao ativo: conexao real desabilitada. "
                            + "Defina WHATSAPP_SIMULAR=false para parear de verdade.");
        }

        // Ja conectado: nao faz sentido gerar um novo QR.
        StatusConexao atual = consultarStatus();
        if (atual.conectadoComSucesso()) {
            return atual;
        }

        garantirInstancia(numero);

        String caminho = "/instance/connect/" + client.instancia();
        String numeroLimpo = normalizarNumero(numero);
        if (!numeroLimpo.isBlank()) {
            caminho += "?number=" + numeroLimpo;
        }

        JsonNode resposta = client.chamar(HttpMethod.GET, caminho, null);
        if (resposta == null) {
            throw new IllegalStateException("A Evolution API nao devolveu dados de pareamento.");
        }

        String qrCode = normalizarQrCode(resposta.path("base64").asText(""));
        String codigoPareamento = resposta.path("pairingCode").asText("");

        if (qrCode.isBlank() && codigoPareamento.isBlank()) {
            // Algumas versoes so devolvem o payload cru do QR em "code".
            String code = resposta.path("code").asText("");
            if (code.isBlank()) {
                throw new IllegalStateException(
                        "A Evolution API nao devolveu QR code nem codigo de pareamento.");
            }
        }

        log.info("Pareamento iniciado para a instancia {} ({}).", client.instancia(),
                codigoPareamento.isBlank() ? "QR code" : "codigo de pareamento");

        return StatusConexao.aguardando(
                qrCode.isBlank() ? null : qrCode,
                codigoPareamento.isBlank() ? null : codigoPareamento);
    }

    /**
     * Cria a instancia caso ainda nao exista.
     * Criar duas vezes devolve erro na Evolution API, entao a existencia e verificada antes.
     */
    private void garantirInstancia(String numero) {
        if (instanciaExiste()) {
            return;
        }

        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("instanceName", client.instancia());
        corpo.put("qrcode", true);
        corpo.put("integration", "WHATSAPP-BAILEYS");

        String numeroLimpo = normalizarNumero(numero);
        if (!numeroLimpo.isBlank()) {
            corpo.put("number", numeroLimpo);
        }

        log.info("Criando a instancia {} na Evolution API.", client.instancia());
        client.chamar(HttpMethod.POST, "/instance/create", corpo);
    }

    private boolean instanciaExiste() {
        try {
            JsonNode resposta = client.chamar(HttpMethod.GET,
                    "/instance/fetchInstances?instanceName=" + client.instancia(), null);
            if (resposta == null) {
                return false;
            }
            // Resposta e um array; vazio significa que a instancia ainda nao existe.
            return resposta.isArray() ? !resposta.isEmpty() : resposta.size() > 0;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Encerra a sessao no celular, exigindo novo pareamento. */
    public void desconectar() {
        if (client.estaSimulando()) {
            log.info("[SIMULACAO] Desconexao solicitada.");
            return;
        }
        client.chamar(HttpMethod.DELETE, "/instance/logout/" + client.instancia(), null);
        log.info("Instancia {} desconectada.", client.instancia());
    }

    // ------------------------------------------------------------------
    // Grupos
    // ------------------------------------------------------------------

    /**
     * Lista os grupos da conta conectada, em ordem alfabetica.
     * E o que permite o usuario escolher o destino sem digitar IDs.
     */
    public List<Grupo> listarGrupos() {
        if (client.estaSimulando()) {
            return List.of(
                    new Grupo("120363000000000001@g.us", "[simulacao] Grupo da Paroquia", 42),
                    new Grupo("120363000000000002@g.us", "[simulacao] Familia", 8));
        }

        JsonNode resposta = client.chamar(HttpMethod.GET,
                "/group/fetchAllGroups/" + client.instancia() + "?getParticipants=false", null);

        List<Grupo> grupos = new ArrayList<>();
        if (resposta == null) {
            return grupos;
        }

        // A resposta e um array de grupos; algumas versoes envolvem em "groups".
        JsonNode lista = resposta.isArray() ? resposta : resposta.path("groups");
        for (JsonNode item : lista) {
            String id = item.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            String nome = item.path("subject").asText("");
            if (nome.isBlank()) {
                nome = item.path("name").asText(id);
            }
            int participantes = item.path("size").asInt(item.path("participantsCount").asInt(0));
            grupos.add(new Grupo(id, nome, participantes));
        }

        grupos.sort(Comparator.comparing(Grupo::nome, String.CASE_INSENSITIVE_ORDER));
        log.info("{} grupos encontrados na instancia {}.", grupos.size(), client.instancia());
        return grupos;
    }

    // ------------------------------------------------------------------
    // Utilitarios
    // ------------------------------------------------------------------

    /** Mantem apenas digitos: a Evolution API rejeita mascaras como +55 (11). */
    private String normalizarNumero(String numero) {
        return numero == null ? "" : numero.replaceAll("\\D", "");
    }

    /** Garante que o QR chegue ao navegador como data URI pronto para <img src>. */
    private String normalizarQrCode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return "";
        }
        return base64.startsWith("data:") ? base64 : "data:image/png;base64," + base64;
    }
}
