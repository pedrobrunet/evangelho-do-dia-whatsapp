package com.botwpp.evangelho.service;

import com.botwpp.evangelho.config.AppProperties;
import com.botwpp.evangelho.model.Evangelho;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsavel por obter o Evangelho do dia.
 *
 * Estrategia em duas camadas:
 *  1. Scraping da pagina da Cancao Nova com JSoup (fonte primaria);
 *  2. API publica de liturgia como fallback, caso o HTML mude ou o site esteja fora.
 *
 * O resultado e cacheado em memoria por dia, evitando bater no site a cada
 * pre-visualizacao feita pelo frontend.
 */
@Service
public class LiturgiaService {

    private static final Logger log = LoggerFactory.getLogger(LiturgiaService.class);

    /** Timeout curto: o scheduler nao pode ficar preso em uma conexao lenta. */
    private static final int TIMEOUT_MS = 15_000;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";

    /** Captura a referencia biblica entre parenteses no titulo. Ex.: "Evangelho (Mt 5,1-12a)". */
    private static final Pattern REFERENCIA = Pattern.compile("\\(([^)]+)\\)");

    /** Marca o fim da pericope na liturgia; tratada com e sem acento. */
    private static final Pattern FIM_DA_LEITURA =
            Pattern.compile("^palavra da salva[cç][aã]o.*", Pattern.CASE_INSENSITIVE);

    private final RestClient restClient;
    private final AppProperties properties;
    private final Clock clock;

    /** Cache simples de um unico dia — o conteudo so muda a cada 24h. */
    private volatile Evangelho cache;

    public LiturgiaService(RestClient restClient, AppProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Retorna o Evangelho de hoje, usando cache quando ja buscado no mesmo dia.
     *
     * @throws IllegalStateException quando nenhuma das fontes responde
     */
    public Evangelho buscarEvangelhoDoDia() {
        LocalDate hoje = LocalDate.now(clock);

        Evangelho emCache = this.cache;
        if (emCache != null && hoje.equals(emCache.data())) {
            log.debug("Evangelho de {} servido do cache.", hoje);
            return emCache;
        }

        Evangelho resultado;
        try {
            resultado = buscarViaScraping(hoje);
        } catch (Exception e) {
            log.warn("Scraping da Cancao Nova falhou ({}). Tentando a API publica de liturgia.", e.getMessage());
            resultado = buscarViaApi(hoje);
        }

        this.cache = resultado;
        return resultado;
    }

    /** Forca uma nova busca ignorando o cache. */
    public Evangelho recarregar() {
        this.cache = null;
        return buscarEvangelhoDoDia();
    }

    // ------------------------------------------------------------------
    // Fonte 1 - scraping com JSoup
    // ------------------------------------------------------------------

    /**
     * Le a pagina da Cancao Nova e extrai o bloco do Evangelho.
     *
     * O site nao expoe um seletor estavel e dedicado, entao a busca e feita por
     * conteudo: localiza o cabecalho cujo texto comeca com "Evangelho" e coleta
     * os paragrafos seguintes ate o proximo cabecalho. Se o layout mudar, o
     * fallback via API assume automaticamente.
     */
    private Evangelho buscarViaScraping(LocalDate data) throws IOException {
        Document doc = Jsoup.connect(properties.getLiturgiaUrl())
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        Element cabecalho = localizarCabecalhoDoEvangelho(doc);
        if (cabecalho == null) {
            throw new IOException("Cabecalho do Evangelho nao encontrado no HTML da pagina.");
        }

        String tituloCompleto = cabecalho.text().trim();
        String referencia = extrairReferencia(tituloCompleto);
        String texto = coletarParagrafosApos(cabecalho);

        if (texto.isBlank()) {
            throw new IOException("Cabecalho encontrado, porem sem paragrafos de texto.");
        }

        log.info("Evangelho de {} obtido via scraping ({}).", data, referencia);
        return new Evangelho(data, referencia, tituloCompleto, texto, "Cancao Nova (scraping)");
    }

    /** Procura, entre os titulos da pagina, aquele que introduz o Evangelho. */
    private Element localizarCabecalhoDoEvangelho(Document doc) {
        Elements candidatos = doc.select("h1, h2, h3, h4, strong, b");
        for (Element candidato : candidatos) {
            String texto = candidato.text().trim().toLowerCase(Locale.ROOT);
            // "Proclamacao do Evangelho..." tambem contem a palavra, por isso o startsWith.
            if (texto.startsWith("evangelho")) {
                return candidato;
            }
        }
        return null;
    }

    /**
     * Percorre os irmaos seguintes ao cabecalho acumulando paragrafos,
     * ate encontrar o proximo titulo (que ja pertence a outra secao).
     */
    private String coletarParagrafosApos(Element cabecalho) {
        // Quando o titulo esta dentro de um <p><strong>, o irmao util e o do <p> pai.
        String tag = cabecalho.tagName();
        Element referencia = ("strong".equals(tag) || "b".equals(tag)) ? cabecalho.parent() : cabecalho;
        if (referencia == null) {
            return "";
        }

        StringBuilder texto = new StringBuilder();
        for (Element irmao : referencia.nextElementSiblings()) {
            if (irmao.tagName().matches("h[1-6]")) {
                break;
            }
            String paragrafo = irmao.text().trim();
            if (paragrafo.isBlank()) {
                continue;
            }
            if (FIM_DA_LEITURA.matcher(paragrafo).matches()) {
                break;
            }
            texto.append(paragrafo).append("\n\n");
        }
        return texto.toString().trim();
    }

    private String extrairReferencia(String titulo) {
        Matcher matcher = REFERENCIA.matcher(titulo);
        return matcher.find() ? matcher.group(1).trim() : "Evangelho do dia";
    }

    // ------------------------------------------------------------------
    // Fonte 2 - API publica de liturgia (fallback)
    // ------------------------------------------------------------------

    /**
     * Consome a API publica de liturgia, que devolve o JSON completo do dia.
     * Formato esperado: leituras.evangelho[0].{referencia,titulo,texto}.
     */
    private Evangelho buscarViaApi(LocalDate data) {
        try {
            JsonNode raiz = restClient.get()
                    .uri(properties.getLiturgiaApiUrl())
                    .retrieve()
                    .body(JsonNode.class);

            if (raiz == null) {
                throw new IllegalStateException("A API de liturgia devolveu um corpo vazio.");
            }

            JsonNode evangelho = raiz.path("leituras").path("evangelho");
            JsonNode item = (evangelho.isArray() && !evangelho.isEmpty()) ? evangelho.get(0) : evangelho;

            String texto = item.path("texto").asText("").trim();
            if (texto.isBlank()) {
                throw new IllegalStateException("A API de liturgia nao trouxe o texto do Evangelho.");
            }

            String referencia = item.path("referencia").asText("Evangelho do dia").trim();
            String titulo = item.path("titulo").asText(referencia).trim();

            log.info("Evangelho de {} obtido via API publica ({}).", data, referencia);
            return new Evangelho(data, referencia, titulo, texto, "API publica de liturgia");

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Nao foi possivel obter o Evangelho do dia em nenhuma das fontes configuradas.", e);
        }
    }
}
