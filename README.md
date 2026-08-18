# Evangelho do Dia — Bot para WhatsApp

Aplicação Spring Boot que busca o Evangelho do dia e o envia automaticamente para um grupo de WhatsApp em um horário programado, configurável por uma página web simples.

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Maven |
| Scraping | JSoup (Canção Nova) + API pública de liturgia como fallback |
| Agendamento | `@Scheduled` do Spring |
| Integração | Evolution API ou webhook genérico (POST HTTP) |
| Frontend | HTML + JavaScript puro + Tailwind CSS (CDN), página única |

## Estrutura do projeto

```
src/main/java/com/botwpp/evangelho/
├── EvangelhoApplication.java        # bootstrap + @EnableScheduling
├── config/
│   ├── AplicacaoConfig.java         # beans RestClient e Clock
│   ├── AppProperties.java           # prefixo "app"
│   ├── WhatsappProperties.java      # prefixo "whatsapp"
│   └── TokenAdminFilter.java        # protege /api/** com X-Admin-Token
├── controller/
│   ├── ConfiguracaoController.java  # GET/PUT /api/configuracao
│   ├── EvangelhoController.java     # prévia, recarregar e envio manual
│   └── TratadorDeErros.java         # @RestControllerAdvice
├── dto/
│   ├── ConfiguracaoRequest.java     # payload validado com Bean Validation
│   └── RespostaApi.java             # envelope {sucesso, mensagem, dados}
├── model/
│   ├── ConfiguracaoEnvio.java       # horário, destino, ativo, último envio
│   └── Evangelho.java               # record com o texto do dia
├── repository/
│   └── ConfiguracaoRepository.java  # persistência em JSON (data/configuracao.json)
├── scheduler/
│   └── EnvioScheduler.java          # cron a cada minuto + guarda de idempotência
└── service/
    ├── LiturgiaService.java         # scraping JSoup + fallback via API
    ├── WhatsappService.java         # POST para Evolution API / webhook
    └── EnvioEvangelhoService.java   # orquestra busca → formatação → envio

src/main/resources/
├── application.yml
└── static/index.html                # frontend (SPA de página única)
```

## Como rodar

```bash
mvn spring-boot:run
```

Acesse http://localhost:8081

Por padrão `whatsapp.simular=true`: a mensagem é apenas escrita no log, sem chamar nenhuma API externa. Ideal para testar antes de conectar a instância real.

## Configuração

Todas as variáveis têm default seguro. Copie `.env.example` e exporte no ambiente:

| Variável | Descrição |
|---|---|
| `SERVER_PORT` | Porta HTTP (padrão 8081) |
| `APP_TIMEZONE` | Fuso do agendamento (padrão America/Sao_Paulo) |
| `ADMIN_TOKEN` | Token exigido no header `X-Admin-Token` para `/api/**` |
| `WHATSAPP_PROVIDER` | `EVOLUTION` ou `WEBHOOK` |
| `WHATSAPP_API_URL` | URL base da Evolution API ou do webhook |
| `WHATSAPP_INSTANCE` | Nome da instância na Evolution API |
| `WHATSAPP_API_KEY` | Chave da API — **nunca versione** |
| `WHATSAPP_SIMULAR` | `true` desliga a chamada externa |

## API REST

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/configuracao` | Estado atual (horário, destino, último envio) |
| `PUT` | `/api/configuracao` | Salva `{horarioEnvio, grupoId, ativo}` |
| `GET` | `/api/evangelho/hoje` | Evangelho do dia estruturado |
| `GET` | `/api/evangelho/previa` | Mensagem já formatada para o WhatsApp |
| `POST` | `/api/evangelho/recarregar` | Limpa o cache e rebusca na fonte |
| `POST` | `/api/evangelho/enviar` | Dispara o envio imediatamente |

Exemplo:

```bash
curl -X PUT http://localhost:8081/api/configuracao \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -d '{"horarioEnvio":"08:00","grupoId":"120363000000000000@g.us","ativo":true}'
```

## Segurança

Este repositório é público. Pontos observados no código:

- **Nenhum segredo versionado.** `apiKey` e `ADMIN_TOKEN` vêm apenas de variáveis de ambiente; `.gitignore` cobre `.env`, `data/` e perfis locais.
- **A API nunca devolve credenciais.** `/api/configuracao` expõe somente horário, destino e status.
- **`/api/**` protegido por `X-Admin-Token`** quando `ADMIN_TOKEN` está definido, com comparação em tempo constante (`MessageDigest.isEqual`). Sem o token definido a API fica aberta e a aplicação registra um aviso na subida — não exponha assim na internet.
- **Sem vazamento de detalhe interno.** `include-stacktrace: never` e o `TratadorDeErros` devolvem mensagens curtas; o detalhe fica no log.
- **Logs sem PII.** O número/ID do grupo é mascarado antes de ser logado; a `apiKey` nunca é registrada.
- **Entrada validada.** `horarioEnvio` e `grupoId` passam por regex no `ConfiguracaoRequest`.
- **`data/configuracao.json` é ignorado pelo git** por conter o destino real das mensagens.

Antes de expor publicamente: defina `ADMIN_TOKEN`, coloque a aplicação atrás de um proxy com TLS e restrinja a origem do tráfego.

## Testes

```bash
mvn test
```

Cobrem as decisões do scheduler (horário atingido, desativado, envio duplicado no mesmo dia, janela de tolerância e resiliência a falha) usando `Clock` fixo.

## Observações sobre o scraping

`LiturgiaService` busca o cabeçalho "Evangelho" no HTML por conteúdo, não por seletor CSS fixo, o que reduz a quebra em mudanças de layout. Se ainda assim falhar, o fallback para a API pública de liturgia entra automaticamente — o registro no log indica qual fonte foi usada.
