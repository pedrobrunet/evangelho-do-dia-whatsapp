# Evangelho do Dia — Bot para WhatsApp

Aplicação Spring Boot que envia o Evangelho do dia para grupos de WhatsApp em horários programados. O usuário conecta o WhatsApp pelo próprio painel (QR code ou código de pareamento) e cadastra quantos agendamentos quiser — o conteúdo é buscado automaticamente todos os dias.

## Fluxo de uso

```
1. Conectar o WhatsApp    →  QR code ou código de pareamento, direto no painel
2. Cadastrar agendamentos →  grupo (lista da conta conectada) + horário
                             quantos quiser: 07h no #geral, 12h no #mentorias…
3. Acompanhar a fila      →  próximos disparos, ordenados, com contagem regressiva
                             (o conteúdo vem sozinho da API de liturgia)
```

Cada agendamento dispara **uma vez por dia**, no seu horário, e pode ser pausado, editado, removido ou disparado na hora pelo botão "Enviar agora".

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
│   ├── AgendamentoController.java   # CRUD dos agendamentos + fila
│   ├── ConexaoController.java       # QR code, pareamento, status e grupos
│   ├── EvangelhoController.java     # conteúdo do dia e prévia
│   └── TratadorDeErros.java         # @RestControllerAdvice
├── dto/
│   ├── AgendamentoRequest.java      # payload validado com Bean Validation
│   ├── ProximoEnvio.java            # uma entrada da fila
│   └── RespostaApi.java             # envelope {sucesso, mensagem, dados}
├── model/
│   ├── Agendamento.java             # grupo + horário + ativo + último envio
│   ├── Evangelho.java               # record com o texto do dia
│   ├── Grupo.java                   # grupo disponível na conta conectada
│   └── StatusConexao.java           # estado do pareamento + QR/código
├── repository/
│   └── AgendamentoRepository.java   # persistência em JSON (data/agendamentos.json)
├── scheduler/
│   └── EnvioScheduler.java          # cron a cada minuto, percorre os ativos
└── service/
    ├── AgendamentoService.java      # regras dos agendamentos e cálculo da fila
    ├── EvolutionApiClient.java      # cliente HTTP único da Evolution API
    ├── ConexaoWhatsappService.java  # instância, QR code, status e grupos
    ├── LiturgiaService.java         # scraping JSoup + fallback via API
    ├── WhatsappService.java         # envio (Evolution ou webhook genérico)
    └── EnvioEvangelhoService.java   # orquestra busca → formatação → envio

src/main/resources/
├── application.yml
└── static/index.html                # painel: conexão, agendamentos e fila

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

### Agendamentos e fila

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/agendamentos` | Lista os agendamentos, ordenados por horário |
| `POST` | `/api/agendamentos` | Cria `{horarioEnvio, grupoId, grupoNome, ativo}` |
| `PUT` | `/api/agendamentos/{id}` | Edita horário, grupo ou estado |
| `POST` | `/api/agendamentos/{id}/alternar` | Pausa ou reativa |
| `DELETE` | `/api/agendamentos/{id}` | Remove |
| `POST` | `/api/agendamentos/{id}/enviar` | Dispara este agendamento agora |
| `GET` | `/api/agendamentos/fila` | Próximos disparos, do mais próximo ao mais distante |

### Conteúdo

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/evangelho/hoje` | Evangelho do dia estruturado |
| `GET` | `/api/evangelho/previa` | Mensagem já formatada para o WhatsApp |
| `POST` | `/api/evangelho/recarregar` | Limpa o cache e rebusca na fonte |

Exemplo:

```bash
curl -X POST http://localhost:8081/api/agendamentos \
  -H "Content-Type: application/json" \
  -H "X-Admin-Token: $ADMIN_TOKEN" \
  -d '{"horarioEnvio":"08:00","grupoId":"120363000000000000@g.us","grupoNome":"Paroquia","ativo":true}'
```

A fila devolve o instante absoluto de cada disparo, e o painel calcula a contagem regressiva no cliente:

```json
[
  {"grupoNome":"#NemTodoDomingoTem!","quando":"2026-08-18T20:00:00","emMinutos":191,"hoje":true},
  {"grupoNome":"#geral","quando":"2026-08-19T07:00:00","emMinutos":851,"hoje":false}
]
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
- **Entrada validada.** `horarioEnvio`, `grupoId` e `grupoNome` passam por regex/tamanho no `AgendamentoRequest`.
- **Sem XSS no painel.** Nomes de grupo são definidos por terceiros no WhatsApp e passam por escape antes de ir para o HTML.

Um painel conectado envia mensagens em nome do WhatsApp pareado. Antes de expor na internet: defina `ADMIN_TOKEN`, use um proxy com TLS e restrinja a origem do tráfego.

## Testes

```bash
mvn test
```

23 testes cobrindo:

- **Scheduler** — horário atingido, pausado, envio duplicado no mesmo dia, janela de tolerância, disparo seletivo entre vários agendamentos e falha de um sem travar os demais, com `Clock` fixo.
- **Fila** — disparo hoje x amanhã, janela de tolerância, exclusão de pausados, ordenação e fallback para o ID quando não há nome do grupo.
- **Conexão** — parsing das respostas da Evolution API nos dois formatos conhecidos, normalização do QR code em data URI, Evolution fora do ar, e listagem/ordenação de grupos.

## Observações sobre o conteúdo

`LiturgiaService` busca o cabeçalho "Evangelho" no HTML da Canção Nova por conteúdo, não por seletor CSS fixo, o que reduz a quebra em mudanças de layout. Se ainda assim falhar, o fallback para a API pública de liturgia entra automaticamente — o log indica qual fonte foi usada. O resultado é cacheado por dia.

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE) para os termos completos.

## Deploy

A aplicação precisa de um **processo permanente**: o `@Scheduled` dispara nos horários programados e o bridge mantém uma conexão WebSocket aberta com o WhatsApp o tempo todo. Isso a torna incompatível com plataformas serverless (Vercel, Netlify, Cloudflare Workers) — a função encerra, a sessão cai e o pareamento se perde.

O `Dockerfile` sobe o painel e o bridge no mesmo container. O bridge escuta apenas em `127.0.0.1:8080`, alcançável só pelo painel; nada dele fica exposto.

### Railway

```bash
railway login
railway init                 # cria o projeto
railway up                   # build e deploy a partir do Dockerfile
railway domain               # gera a URL pública
```

Depois, no painel do Railway:

1. **Volume** — monte um volume em `/data`. Sem ele, as credenciais da sessão e os agendamentos somem a cada deploy, e você precisa parear de novo.
2. **Variáveis** — `WHATSAPP_API_KEY` (qualquer valor forte; é usada só entre painel e bridge dentro do container) e, quando quiser proteger o acesso, `ADMIN_TOKEN`.
3. **Réplicas: 1.** Já vem fixo em `railway.json`. Com duas réplicas você teria duas sessões do WhatsApp e mensagens duplicadas em cada grupo.

`PORT` é injetada pela plataforma e o `application.yml` já a respeita.

### Variáveis no deploy

| Variável | Valor |
|---|---|
| `WHATSAPP_API_KEY` | chave forte, compartilhada entre painel e bridge |
| `ADMIN_TOKEN` | exigido no header `X-Admin-Token`; vazio deixa a API aberta |
| `DATA_DIR` | `/data` (padrão da imagem) |
| `APP_TIMEZONE` | `America/Sao_Paulo` |

### Antes de expor publicamente

Quem alcança a URL sem `ADMIN_TOKEN` pode listar seus grupos, disparar mensagens em nome do WhatsApp pareado e derrubar a sessão. Defina `ADMIN_TOKEN` assim que sair da fase de testes.
