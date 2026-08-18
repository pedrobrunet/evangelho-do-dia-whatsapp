# Evangelho do Dia — Bot para WhatsApp

Aplicação Spring Boot que envia o Evangelho do dia para um grupo de WhatsApp em um horário programado. O usuário conecta o WhatsApp pelo próprio painel (QR code ou código de pareamento), escolhe o grupo numa lista e define o horário — o conteúdo é buscado automaticamente todos os dias.

## Fluxo de uso

```
1. Conectar o WhatsApp   →  QR code ou código de pareamento, direto no painel
2. Escolher o grupo      →  lista carregada da conta conectada, sem digitar IDs
3. Programar o horário   →  único campo que o usuário define
                            (o conteúdo vem da API de liturgia)
```

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Maven |
| Conteúdo | JSoup (scraping da Canção Nova) + API pública de liturgia como fallback |
| Agendamento | `@Scheduled` do Spring |
| WhatsApp | Evolution API (pareamento, grupos e envio), orquestrada pelo backend |
| Frontend | HTML + JavaScript puro + Tailwind CSS (CDN), página única |

### Por que existe um serviço Node ao lado

Não existe biblioteca Java madura que implemente o protocolo do WhatsApp Web — as implementações reais são Node (Baileys). Algo precisa gerar o QR code, manter a sessão e despachar as mensagens. O backend Java orquestra esse serviço via HTTP, de forma que **quem usa o painel nunca o acessa diretamente**.

Duas opções, mesmo contrato HTTP — escolha uma:

**A) Bridge incluso (sem Docker).** Serviço Node de um arquivo, usando Baileys direto. Veja [`whatsapp-bridge/`](whatsapp-bridge/).

```bash
cp .env.example .env                       # defina WHATSAPP_API_KEY e WHATSAPP_SIMULAR=false
cd whatsapp-bridge && npm install
API_KEY=<a mesma chave> npm start          # bridge em 127.0.0.1:8080

# em outro terminal, na raiz do projeto:
mvn spring-boot:run                        # painel em http://localhost:8081
```

**B) Evolution API (Docker).** Mais completa, se você já a usa ou quer múltiplas instâncias:

```bash
cp .env.example .env      # defina WHATSAPP_API_KEY com um valor forte
docker compose up -d      # Evolution API em 127.0.0.1:8080
mvn spring-boot:run       # painel em http://localhost:8081
```

Abra http://localhost:8081 e siga os três passos.

> Para explorar a interface sem conectar um WhatsApp real, deixe `WHATSAPP_SIMULAR=true` (padrão): o backend devolve uma conexão e grupos fictícios, e os envios só vão para o log.

> **Aviso:** Baileys e Evolution API são bibliotecas não oficiais. Automatizar uma conta pessoal pode violar os Termos de Serviço do WhatsApp e levar ao bloqueio do número. Para uso comercial, considere a API oficial do WhatsApp Business.

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
│   ├── ConexaoController.java       # QR code, pareamento, status e grupos
│   ├── ConfiguracaoController.java  # GET/PUT /api/configuracao
│   ├── EvangelhoController.java     # prévia, recarregar e envio manual
│   └── TratadorDeErros.java         # @RestControllerAdvice
├── dto/
│   ├── ConfiguracaoRequest.java     # payload validado com Bean Validation
│   └── RespostaApi.java             # envelope {sucesso, mensagem, dados}
├── model/
│   ├── ConfiguracaoEnvio.java       # horário, destino, ativo, último envio
│   ├── Evangelho.java               # record com o texto do dia
│   ├── Grupo.java                   # grupo disponível na conta conectada
│   └── StatusConexao.java           # estado do pareamento + QR/código
├── repository/
│   └── ConfiguracaoRepository.java  # persistência em JSON (data/configuracao.json)
├── scheduler/
│   └── EnvioScheduler.java          # cron a cada minuto + guarda de idempotência
└── service/
    ├── EvolutionApiClient.java      # cliente HTTP único da Evolution API
    ├── ConexaoWhatsappService.java  # instância, QR code, status e grupos
    ├── LiturgiaService.java         # scraping JSoup + fallback via API
    ├── WhatsappService.java         # envio (Evolution ou webhook genérico)
    └── EnvioEvangelhoService.java   # orquestra busca → formatação → envio

src/main/resources/
├── application.yml
└── static/index.html                # painel de 3 passos

whatsapp-bridge/                     # opcional: alternativa Node à Evolution API
├── server.js                        # Baileys + Express, mesmo contrato HTTP
└── README.md
```

## Configuração

Todas as variáveis têm default seguro. Copie `.env.example` e exporte no ambiente:

| Variável | Descrição |
|---|---|
| `SERVER_PORT` | Porta HTTP do painel (padrão 8081) |
| `APP_TIMEZONE` | Fuso do agendamento (padrão America/Sao_Paulo) |
| `ADMIN_TOKEN` | Token exigido no header `X-Admin-Token` para `/api/**` |
| `WHATSAPP_PROVIDER` | `EVOLUTION` (padrão) ou `WEBHOOK` |
| `WHATSAPP_API_URL` | URL da Evolution API (padrão http://localhost:8080) |
| `WHATSAPP_INSTANCE` | Nome da instância criada no pareamento |
| `WHATSAPP_API_KEY` | Chave da Evolution API — **nunca versione** |
| `WHATSAPP_SIMULAR` | `true` desliga toda chamada externa |

## API REST

### Conexão

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/conexao` | Estado do pareamento (consultado em polling pelo painel) |
| `POST` | `/api/conexao/iniciar` | Gera QR code; com `{"numero":"5511..."}` gera código de pareamento |
| `DELETE` | `/api/conexao` | Encerra a sessão |
| `GET` | `/api/conexao/grupos` | Grupos da conta conectada, em ordem alfabética |

### Programação e conteúdo

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/configuracao` | Estado atual (horário, grupo, último envio) |
| `PUT` | `/api/configuracao` | Salva `{horarioEnvio, grupoId, grupoNome, ativo}` |
| `GET` | `/api/evangelho/hoje` | Evangelho do dia estruturado |
| `GET` | `/api/evangelho/previa` | Mensagem já formatada para o WhatsApp |
| `POST` | `/api/evangelho/recarregar` | Limpa o cache e rebusca na fonte |
| `POST` | `/api/evangelho/enviar` | Dispara o envio imediatamente |

Exemplo:

```bash
curl -X PUT http://localhost:8081/api/configuracao \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -d '{"horarioEnvio":"08:00","grupoId":"120363000000000000@g.us","grupoNome":"Paroquia","ativo":true}'
```

## Segurança

Este repositório é público. Pontos observados no código:

- **Nenhum segredo versionado.** `WHATSAPP_API_KEY` e `ADMIN_TOKEN` vêm apenas de variáveis de ambiente; `.gitignore` cobre `.env`, `data/` e perfis locais.
- **A API nunca devolve credenciais.** `/api/configuracao` expõe somente horário, grupo e status.
- **`/api/**` protegido por `X-Admin-Token`** quando `ADMIN_TOKEN` está definido, com comparação em tempo constante (`MessageDigest.isEqual`). Sem o token a API fica aberta e a aplicação registra um aviso na subida.
- **O serviço de WhatsApp escuta só em `127.0.0.1`**, tanto no `docker-compose.yml` quanto no bridge. Quem alcança essa porta com a API key controla o WhatsApp pareado.
- **As credenciais da sessão pareada** (`whatsapp-bridge/sessoes/`) estão no `.gitignore` e devem ser tratadas como segredo: quem copia essa pasta assume a conta conectada.
- **Sem vazamento de detalhe interno.** `include-stacktrace: never` e o `TratadorDeErros` devolvem mensagens curtas; o detalhe fica no log.
- **Logs sem PII.** O ID do grupo é mascarado antes de ser logado; a API key nunca é registrada.
- **Entrada validada.** `horarioEnvio`, `grupoId` e `grupoNome` passam por regex/tamanho no `ConfiguracaoRequest`.

Um painel conectado envia mensagens em nome do WhatsApp pareado. Antes de expor na internet: defina `ADMIN_TOKEN`, use um proxy com TLS e restrinja a origem do tráfego.

## Testes

```bash
mvn test
```

14 testes cobrindo:

- **Scheduler** — horário atingido, desativado, envio duplicado no mesmo dia, janela de tolerância e resiliência a falha, com `Clock` fixo.
- **Conexão** — parsing das respostas da Evolution API nos dois formatos conhecidos, normalização do QR code em data URI, Evolution fora do ar, e listagem/ordenação de grupos.

## Observações sobre o conteúdo

`LiturgiaService` busca o cabeçalho "Evangelho" no HTML da Canção Nova por conteúdo, não por seletor CSS fixo, o que reduz a quebra em mudanças de layout. Se ainda assim falhar, o fallback para a API pública de liturgia entra automaticamente — o log indica qual fonte foi usada. O resultado é cacheado por dia.

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE) para os termos completos.
