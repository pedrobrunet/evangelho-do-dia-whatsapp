package com.botwpp.evangelho.service;

import com.botwpp.evangelho.config.EmailProperties;
import com.botwpp.evangelho.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Envio de e-mails transacionais pela API da Resend.
 *
 * Nenhuma falha aqui pode derrubar o cadastro: a conta ja foi criada quando
 * o e-mail sai, e o usuario nao deve ficar sem acesso porque o provedor de
 * e-mail esteve indisponivel. Por isso os metodos registram o erro e seguem.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient restClient;
    private final EmailProperties properties;

    public EmailService(RestClient restClient, EmailProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** Boas-vindas enviadas logo apos o cadastro. */
    public void enviarBoasVindas(Usuario usuario, String urlPainel) {
        String assunto = "Sua conta no Evangelho do Dia foi criada";
        enviar(usuario.getEmail(), assunto, montarCorpo(usuario, urlPainel));
    }

    private void enviar(String destinatario, String assunto, String html) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            // Sem credencial o envio vira log: util em desenvolvimento e evita
            // que a ausencia de configuracao pareca um envio bem-sucedido.
            log.warn("RESEND_API_KEY nao definida. E-mail para {} nao enviado. Assunto: {}",
                    mascarar(destinatario), assunto);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", properties.getRemetente());
        payload.put("to", List.of(destinatario));
        payload.put("subject", assunto);
        payload.put("html", html);

        try {
            restClient.post()
                    .uri(ENDPOINT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("E-mail de boas-vindas enviado para {}", mascarar(destinatario));

        } catch (RestClientResponseException e) {
            // 403 costuma significar dominio nao verificado na Resend: nesse caso
            // so e possivel enviar para o e-mail dono da conta Resend.
            log.error("Resend recusou o envio para {} (HTTP {}): {}",
                    mascarar(destinatario), e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Falha ao contactar a Resend para enviar a {}", mascarar(destinatario), e);
        }
    }

    private String montarCorpo(Usuario usuario, String urlPainel) {
        return """
                <div style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:520px;margin:0 auto;color:#1e293b">
                  <h1 style="font-size:20px;margin:0 0 4px">📖 Evangelho do Dia</h1>
                  <p style="color:#64748b;margin:0 0 24px">Sua conta foi criada com sucesso.</p>

                  <p>Ola, %s!</p>
                  <p>
                    Sua conta esta pronta e ja pode ser usada. No painel voce conecta o seu WhatsApp,
                    escolhe os grupos e programa os horarios — o Evangelho do dia e enviado
                    automaticamente, todos os dias.
                  </p>

                  <p style="margin:28px 0">
                    <a href="%s" style="background:#0284c7;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;font-weight:600">
                      Acessar o painel
                    </a>
                  </p>

                  <p style="color:#64748b;font-size:13px;border-top:1px solid #e2e8f0;padding-top:16px">
                    Entre com o e-mail <strong>%s</strong>.<br>
                    Se voce nao criou esta conta, ignore esta mensagem.
                  </p>
                </div>"""
                .formatted(escapar(usuario.getNome()), urlPainel, escapar(usuario.getEmail()));
    }

    /** O nome vem do cadastro; escapar evita injecao de marcacao no e-mail. */
    private String escapar(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Evita gravar o endereco completo nos logs. */
    private String mascarar(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
