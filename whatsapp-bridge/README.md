# Bridge WhatsApp

Serviço Node que fala o protocolo do WhatsApp Web usando [Baileys](https://github.com/WhiskeySockets/Baileys) e expõe as rotas HTTP que o painel Java consome.

É uma alternativa leve à Evolution API: mesmo contrato de API, sem Docker e sem banco de dados. Se você já roda a Evolution API, pode ignorar esta pasta e apontar `WHATSAPP_API_URL` para ela.

## Rodar

```bash
cd whatsapp-bridge
npm install
API_KEY=sua-chave-secreta PORT=8080 npm start
```

Variáveis:

| Variável | Padrão | Descrição |
|---|---|---|
| `PORT` | `8080` | Porta HTTP |
| `HOST` | `127.0.0.1` | Interface de escuta |
| `API_KEY` | vazio | Exigida no header `apikey`; sem ela o serviço fica aberto |
| `SESSION_DIR` | `./sessoes` | Onde as credenciais da sessão são gravadas |

## Rotas

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/instance/fetchInstances` | Instâncias conhecidas |
| `POST` | `/instance/create` | Cria a instância |
| `GET` | `/instance/connect/:nome?number=` | QR code (base64) ou código de pareamento |
| `GET` | `/instance/connectionState/:nome` | `open` / `connecting` / `close` |
| `DELETE` | `/instance/logout/:nome` | Encerra a sessão |
| `GET` | `/group/fetchAllGroups/:nome` | Grupos da conta conectada |
| `POST` | `/message/sendText/:nome` | Envia `{number, text}` |

## Segurança

- Escuta apenas em `127.0.0.1` por padrão. **Não exponha esta porta na internet** — quem a alcança com a `API_KEY` controla o WhatsApp pareado.
- `sessoes/` guarda as credenciais da conta conectada e está no `.gitignore`. Tratar como segredo: quem copia essa pasta assume a sessão.
- Use uma `API_KEY` forte (`openssl rand -hex 32`). Sem ela, qualquer processo local controla o WhatsApp.

## Aviso

Isto usa uma biblioteca não oficial do WhatsApp. Automação de conta pessoal pode violar os Termos de Serviço do WhatsApp e resultar em bloqueio do número. Para uso comercial, considere a API oficial do WhatsApp Business.
