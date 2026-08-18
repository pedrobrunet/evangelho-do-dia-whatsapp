package com.botwpp.evangelho;

import com.botwpp.evangelho.model.Grupo;
import com.botwpp.evangelho.model.StatusConexao;
import com.botwpp.evangelho.service.ConexaoWhatsappService;
import com.botwpp.evangelho.service.EvolutionApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o parsing das respostas da Evolution API, que variam entre versoes.
 */
class ConexaoWhatsappServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INSTANCIA = "uteste";

    private EvolutionApiClient client;
    private ConexaoWhatsappService service;

    @BeforeEach
    void preparar() {
        client = mock(EvolutionApiClient.class);
        when(client.estaSimulando()).thenReturn(false);
        service = new ConexaoWhatsappService(client);
    }

    private JsonNode json(String conteudo) {
        try {
            return JSON.readTree(conteudo);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void deveReconhecerSessaoAtivaNoFormatoAninhado() {
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connectionState/"), isNull()))
                .thenReturn(json("{\"instance\":{\"instanceName\":\"teste\",\"state\":\"open\"}}"));

        assertThat(service.consultarStatus(INSTANCIA).estado()).isEqualTo(StatusConexao.Estado.CONECTADO);
    }

    @Test
    void deveReconhecerSessaoAtivaNoFormatoPlano() {
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connectionState/"), isNull()))
                .thenReturn(json("{\"state\":\"open\"}"));

        assertThat(service.consultarStatus(INSTANCIA).estado()).isEqualTo(StatusConexao.Estado.CONECTADO);
    }

    @Test
    void deveTratarSessaoFechadaComoDesconectado() {
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connectionState/"), isNull()))
                .thenReturn(json("{\"instance\":{\"state\":\"close\"}}"));

        assertThat(service.consultarStatus(INSTANCIA).estado()).isEqualTo(StatusConexao.Estado.DESCONECTADO);
    }

    @Test
    void deveReportarIndisponivelQuandoEvolutionApiFalha() {
        when(client.chamar(any(), any(), any()))
                .thenThrow(new IllegalStateException("Evolution API fora do ar"));

        StatusConexao status = service.consultarStatus(INSTANCIA);

        assertThat(status.estado()).isEqualTo(StatusConexao.Estado.INDISPONIVEL);
        assertThat(status.descricao()).contains("fora do ar");
    }

    @Test
    void deveNormalizarQrCodeComoDataUri() {
        // Instancia ja existe e esta desconectada.
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connectionState/"), isNull()))
                .thenReturn(json("{\"instance\":{\"state\":\"close\"}}"));
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/fetchInstances"), isNull()))
                .thenReturn(json("[{\"name\":\"teste\"}]"));
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connect/"), isNull()))
                .thenReturn(json("{\"base64\":\"iVBORw0KGgo=\",\"pairingCode\":\"\"}"));

        StatusConexao status = service.iniciarConexao(INSTANCIA, null);

        assertThat(status.estado()).isEqualTo(StatusConexao.Estado.AGUARDANDO_LEITURA);
        assertThat(status.qrCodeBase64()).isEqualTo("data:image/png;base64,iVBORw0KGgo=");
    }

    @Test
    void devePreservarDataUriJaFormatado() {
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connectionState/"), isNull()))
                .thenReturn(json("{\"instance\":{\"state\":\"close\"}}"));
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/fetchInstances"), isNull()))
                .thenReturn(json("[{\"name\":\"teste\"}]"));
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connect/"), isNull()))
                .thenReturn(json("{\"base64\":\"data:image/png;base64,AAAA\",\"pairingCode\":\"ABCD1234\"}"));

        StatusConexao status = service.iniciarConexao(INSTANCIA, null);

        assertThat(status.qrCodeBase64()).isEqualTo("data:image/png;base64,AAAA");
        assertThat(status.codigoPareamento()).isEqualTo("ABCD1234");
    }

    @Test
    void naoDeveGerarNovoPareamentoQuandoJaConectado() {
        when(client.chamar(eq(HttpMethod.GET), contains("/instance/connectionState/"), isNull()))
                .thenReturn(json("{\"instance\":{\"state\":\"open\"}}"));

        StatusConexao status = service.iniciarConexao(INSTANCIA, null);

        assertThat(status.estado()).isEqualTo(StatusConexao.Estado.CONECTADO);
        assertThat(status.qrCodeBase64()).isNull();
    }

    @Test
    void deveListarGruposOrdenadosPorNome() {
        when(client.chamar(eq(HttpMethod.GET), contains("/group/fetchAllGroups/"), isNull()))
                .thenReturn(json("""
                        [
                          {"id":"2@g.us","subject":"Zeladoria","size":5},
                          {"id":"1@g.us","subject":"Adoracao","size":12},
                          {"id":"","subject":"Sem id"}
                        ]"""));

        List<Grupo> grupos = service.listarGrupos(INSTANCIA);

        assertThat(grupos).hasSize(2);
        assertThat(grupos.get(0).nome()).isEqualTo("Adoracao");
        assertThat(grupos.get(0).participantes()).isEqualTo(12);
        assertThat(grupos.get(1).nome()).isEqualTo("Zeladoria");
    }

    @Test
    void deveUsarGruposFicticiosEmModoSimulacao() {
        when(client.estaSimulando()).thenReturn(true);

        assertThat(service.listarGrupos(INSTANCIA)).isNotEmpty();
        assertThat(service.consultarStatus(INSTANCIA).estado()).isEqualTo(StatusConexao.Estado.CONECTADO);
    }
}
