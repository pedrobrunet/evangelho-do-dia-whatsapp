# Evangelho do Dia — Bot para WhatsApp

Aplicação Spring Boot que envia o Evangelho do dia para grupos de WhatsApp em horários programados. Cada conta conecta o próprio WhatsApp pelo painel (QR code ou código de pareamento) e cadastra quantos agendamentos quiser — o conteúdo é buscado automaticamente todos os dias.

## Fluxo de uso

```
0. Criar conta            →  nome, e-mail e senha; boas-vindas por e-mail
1. Conectar o WhatsApp    →  QR code ou código de pareamento, direto no painel
2. Cadastrar agendamentos →  grupo (lista da conta conectada) + horário
                             quantos quiser: 07h no #geral, 12h no #mentorias…
3. Acompanhar a fila      →  próximos disparos, ordenados, com contagem regressiva
                             (o conteúdo vem sozinho da API de liturgia)
```

Cada conta é isolada: conecta o próprio WhatsApp e só enxerga os próprios agendamentos.

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
│   ├── EmailProperties.java         # prefixo "email"
│   ├── WhatsappProperties.java      # prefixo "whatsapp"
│   ├── SessaoFilter.java            # exige sessão em toda a API
│   └── UsuarioLogado.java           # resolve o usuário da sessão
├── controller/
│   ├── AutenticacaoController.java  # cadastro, login, sair
│   ├── AgendamentoController.java   # CRUD dos agendamentos + fila
│   ├── ConexaoController.java       # QR code, pareamento, status e grupos
│   ├── EvangelhoController.java     # conteúdo do dia, prévia e envio avulso
│   └── TratadorDeErros.java         # @RestControllerAdvice
├── dto/
│   ├── AgendamentoRequest.java      # payload validado com Bean Validation
│   ├── EnvioManualRequest.java      # destino do envio avulso
│   ├── LoginRequest.java            # credenciais
│   ├── ProximoEnvio.java            # uma entrada da fila
│   ├── RegistroRequest.java         # cadastro validado
│   ├── RespostaApi.java             # envelope {sucesso, mensagem, dados}
│   └── UsuarioResponse.java         # usuário sem o hash de senha
├── model/
│   ├── Agendamento.java             # dono + grupo + horário + ativo
│   ├── Evangelho.java               # record com o texto do dia
│   ├── Grupo.java                   # grupo disponível na conta conectada
│   ├── StatusConexao.java           # estado do pareamento + QR/código
│   └── Usuario.java                 # conta; deriva a instância do WhatsApp
├── repository/
│   ├── AgendamentoRepository.java   # persistência em JSON, filtrada por dono
│   └── UsuarioRepository.java       # contas (data/usuarios.json)
├── scheduler/
│   └── EnvioScheduler.java          # cron a cada minuto, percorre todas as contas
└── service/
    ├── AgendamentoService.java      # regras dos agendamentos e cálculo da fila
    ├── AutenticacaoService.java     # cadastro e login com BCrypt
    ├── EmailService.java            # boas-vindas via Resend
    ├── EvolutionApiClient.java      # cliente HTTP único da Evolution API
    ├── ConexaoWhatsappService.java  # instância, QR code, status e grupos
    ├── LiturgiaService.java         # scraping JSoup + fallback via API
    ├── WhatsappService.java         # envio (Evolution ou webhook genérico)
    └── EnvioEvangelhoService.java   # orquestra busca → formatação → envio

src/main/resources/
├── application.yml
└── static/index.html                # painel: login, conexão, agendamentos e fila

src/test/java/com/botwpp/evangelho/
├── AgendamentoServiceTest.java      # cálculo da fila
├── AutenticacaoServiceTest.java     # cadastro e login
├── ConexaoWhatsappServiceTest.java  # respostas da Evolution API
├── EnvioSchedulerTest.java          # disparo por horário e por conta
├── RegistroRequestTest.java         # validação do cadastro
└── repository/
    └── UsuarioRepositoryTest.java   # contas em disco entre reinícios

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
| `APP_USUARIOS_FILE` | Arquivo das contas (padrão `data/usuarios.json`); contém hashes de senha |
| `RESEND_API_KEY` | Chave da Resend; vazia deixa os e-mails apenas no log |
| `EMAIL_REMETENTE` | Remetente dos e-mails |
| `EMAIL_URL_PAINEL` | URL usada nos links dos e-mails |
| `WHATSAPP_PROVIDER` | `EVOLUTION` (padrão) ou `WEBHOOK` |
| `WHATSAPP_API_URL` | URL da Evolution API (padrão http://localhost:8080) |
| `WHATSAPP_INSTANCE` | Nome da instância criada no pareamento |
| `WHATSAPP_API_KEY` | Chave da Evolution API — **nunca versione** |
| `WHATSAPP_SIMULAR` | `true` desliga toda chamada externa |

## API REST

Exceto as três rotas de autenticação, toda a API exige sessão. O cookie é definido no login e enviado automaticamente pelo navegador.

### Autenticação

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/api/auth/registrar` | Cria a conta `{nome, email, senha}` e já autentica |
| `POST` | `/api/auth/login` | Autentica `{email, senha}` |
| `POST` | `/api/auth/sair` | Encerra a sessão |
| `GET` | `/api/auth/eu` | Quem está logado; 204 quando não há sessão |

### Conexão

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/conexao` | Estado do pareamento (consultado em polling pelo painel) |
| `POST` | `/api/conexao/iniciar` | Gera QR code; com `{"numero":"5511..."}` gera código de pareamento |
| `DELETE` | `/api/conexao` | Encerra a sessão |
| `GET` | `/api/conexao/grupos` | Grupos da conta conectada, em ordem alfabética |
| `POST` | `/api/evangelho/enviar` | Envio avulso `{grupoId}`, sem agendamento |

### Agendamentos e fila

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/agendamentos` | Agendamentos da conta logada, por horário |
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

Exemplo — o login guarda o cookie de sessão, e as chamadas seguintes o reenviam:

```bash
curl -c sessao.txt -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"voce@exemplo.com","senha":"sua-senha"}'

curl -b sessao.txt -X POST http://localhost:8081/api/agendamentos \
  -H "Content-Type: application/json" \
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

- **Nenhum segredo versionado.** `WHATSAPP_API_KEY` e `RESEND_API_KEY` vêm apenas de variáveis de ambiente; `.gitignore` cobre `.env`, `data/` e perfis locais.
- **A API nunca devolve credenciais.** O modelo `Usuario` guarda o hash da senha, mas os controllers respondem com `UsuarioResponse`, que só carrega id, nome e e-mail.
- **Toda a API exige sessão autenticada.** Só `/api/auth/registrar`, `/api/auth/login` e `/api/auth/eu` ficam abertas — são justamente as que criam a sessão.
- **Isolamento entre contas.** O id do dono vem sempre da sessão, nunca do payload, e o repositório filtra por ele: um id de agendamento vazado não permite ler, editar nem remover de outra conta.
- **Senhas com BCrypt.** A senha em texto puro nunca é armazenada. Login com e-mail inexistente compara contra um hash fictício, para que o tempo de resposta não revele quais e-mails têm conta.
- **Sessão renovada a cada login**, o que impede fixação de sessão.
- **O serviço de WhatsApp escuta só em `127.0.0.1`**, tanto no `docker-compose.yml` quanto no bridge. Quem alcança essa porta com a API key controla o WhatsApp pareado.
- **As credenciais da sessão pareada** (`whatsapp-bridge/sessoes/`) estão no `.gitignore` e devem ser tratadas como segredo: quem copia essa pasta assume a conta conectada.
- **Sem vazamento de detalhe interno.** `include-stacktrace: never` e o `TratadorDeErros` devolvem mensagens curtas; o detalhe fica no log.
- **Logs sem PII.** O ID do grupo é mascarado antes de ser logado; a API key nunca é registrada.
- **Entrada validada.** `horarioEnvio`, `grupoId` e `grupoNome` passam por regex/tamanho no `AgendamentoRequest`; nome, e-mail e senha, no `RegistroRequest`. O limite de 100 caracteres da senha evita o custo de hashear entradas gigantes.
- **Sem XSS no painel.** Nomes de grupo são definidos por terceiros no WhatsApp e passam por escape antes de ir para o HTML.

Um painel conectado envia mensagens em nome do WhatsApp pareado. Ao expor na internet, use um proxy com TLS — a sessão viaja em cookie e sem HTTPS pode ser interceptada.

## Testes

```bash
mvn test
```

44 testes cobrindo:

- **Scheduler** — horário atingido, pausado, envio duplicado no mesmo dia, janela de tolerância, disparo seletivo entre vários agendamentos e falha de um sem travar os demais, com `Clock` fixo.
- **Isolamento** — cada envio sai pela sessão do dono; agendamento órfão de conta removida é ignorado.
- **Fila** — disparo hoje x amanhã, janela de tolerância, exclusão de pausados, ordenação e fallback para o ID quando não há nome do grupo.
- **Conexão** — parsing das respostas da Evolution API nos dois formatos conhecidos, normalização do QR code em data URI, Evolution fora do ar, e listagem/ordenação de grupos.
- **Cadastro e login** — senha guardada só em hash, e-mail normalizado, instância de WhatsApp própria por conta, recusa de e-mail já usado em qualquer caixa, cadastro concluído mesmo com o provedor de e-mail fora do ar, e a igualdade das mensagens de erro do login.
- **Contas em disco** — gravação e recarga após reinício, busca sem distinguir maiúsculas, arquivo ausente ou corrompido sem derrubar a aplicação.
- **Validação do cadastro** — e-mail colado com espaços é aceito, e-mail inválido continua recusado, senha nunca sofre trim.

Os testes de conta foram conferidos por mutação: cada um foi validado quebrando o código de propósito, para garantir que acusa a regressão em vez de passar por acaso.

## Observações sobre o conteúdo

`LiturgiaService` busca o cabeçalho "Evangelho" no HTML da Canção Nova por conteúdo, não por seletor CSS fixo, o que reduz a quebra em mudanças de layout. Se ainda assim falhar, o fallback para a API pública de liturgia entra automaticamente — o log indica qual fonte foi usada. O resultado é cacheado por dia.

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE) para os termos completos.

## Deploy

A aplicação precisa de um **processo permanente**: o `@Scheduled` dispara nos horários programados e o bridge mantém uma conexão WebSocket aberta com o WhatsApp o tempo todo. Isso a torna incompatível com plataformas serverless (Vercel, Netlify, Cloudflare Workers) — a função encerra, a sessão cai e o pareamento se perde.

O `Dockerfile` sobe o painel e o bridge no mesmo container. O bridge escuta apenas em `127.0.0.1:8080`, alcançável só pelo painel; nada dele fica exposto.

### Fly.io

O `fly.toml` já vem configurado: região `gru`, uma única máquina, volume em `/data` e `auto_stop_machines = false` — a máquina não pode hibernar, ou o WhatsApp derruba o aparelho conectado e os agendamentos param de disparar em silêncio.

```bash
fly launch --no-deploy                       # reaproveita o fly.toml do repositório
fly volumes create dados --size 1 --region gru
fly secrets set WHATSAPP_API_KEY=<chave forte> RESEND_API_KEY=<chave da Resend>
fly deploy
```

### Railway

```bash
railway login
railway init                 # cria o projeto
railway up                   # build e deploy a partir do Dockerfile
railway domain               # gera a URL pública
```

Depois, no painel do Railway:

1. **Volume** — monte um volume em `/data`. Sem ele, as contas, os agendamentos e as credenciais da sessão somem a cada deploy, e todo mundo precisa se cadastrar e parear de novo.
2. **Variáveis** — `WHATSAPP_API_KEY` (qualquer valor forte; usada só entre painel e bridge dentro do container) e `RESEND_API_KEY` para o envio de e-mails.
3. **Réplicas: 1.** Já vem fixo em `railway.json`. Com duas réplicas você teria duas sessões do WhatsApp e mensagens duplicadas em cada grupo.

`PORT` é injetada pela plataforma e o `application.yml` já a respeita.

### Variáveis no deploy

| Variável | Valor |
|---|---|
| `WHATSAPP_API_KEY` | chave forte, compartilhada entre painel e bridge |
| `RESEND_API_KEY` | chave da Resend; sem ela o e-mail de boas-vindas só vai para o log |
| `EMAIL_REMETENTE` | remetente verificado na Resend |
| `EMAIL_URL_PAINEL` | URL pública do painel, usada nos links dos e-mails |
| `DATA_DIR` | `/data` (padrão da imagem) |
| `APP_TIMEZONE` | `America/Sao_Paulo` |

`APP_AGENDAMENTOS_FILE` e `APP_USUARIOS_FILE` já apontam para `/data` na imagem. Se você sobrescrever qualquer uma delas, mantenha o caminho **dentro do volume** — fora dele, o arquivo nasce no container e some no deploy seguinte, levando junto todas as contas cadastradas.

### Antes de expor publicamente

O acesso é por conta: quem não tem login não passa do `SessaoFilter`. Duas providências continuam necessárias:

- **HTTPS obrigatório.** A sessão viaja em cookie; sem TLS, qualquer intermediário na rede pode capturá-lo e assumir a conta. Fly.io e Railway já entregam TLS no domínio que geram; num proxy próprio, configure-o.
- **Volume persistente montado.** Sem ele, contas e pareamento se perdem a cada deploy.

Qualquer pessoa pode criar uma conta na URL pública. O painel não tem cadastro por convite nem confirmação de e-mail — se a instância for só sua, mantenha a URL discreta ou coloque uma camada de autenticação no proxy.
