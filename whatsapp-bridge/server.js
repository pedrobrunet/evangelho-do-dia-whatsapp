/**
 * Bridge WhatsApp — alternativa leve a Evolution API.
 *
 * Implementa o mesmo contrato HTTP que o painel Java consome, usando Baileys
 * (a mesma biblioteca que a Evolution API utiliza internamente). Serve para
 * rodar tudo sem Docker e sem banco de dados.
 *
 * Rotas expostas:
 *   GET    /instance/fetchInstances?instanceName=x
 *   POST   /instance/create
 *   GET    /instance/connect/:instancia?number=55...
 *   GET    /instance/connectionState/:instancia
 *   DELETE /instance/logout/:instancia
 *   GET    /group/fetchAllGroups/:instancia
 *   POST   /message/sendText/:instancia
 *
 * SEGURANCA: exige o header "apikey" igual a variavel API_KEY e escuta
 * apenas em 127.0.0.1. Quem alcanca esta porta controla o WhatsApp pareado.
 */

import express from 'express';
import qrcode from 'qrcode';
import pino from 'pino';
import { Boom } from '@hapi/boom';
// No Baileys 7 estas funcoes sao named exports; o default e apenas makeWASocket.
import {
  makeWASocket,
  useMultiFileAuthState,
  DisconnectReason,
  fetchLatestBaileysVersion,
} from '@whiskeysockets/baileys';

const PORTA = Number(process.env.PORT || 8080);
const HOST = process.env.HOST || '127.0.0.1';
const API_KEY = process.env.API_KEY || '';
const PASTA_SESSAO = process.env.SESSION_DIR || './sessoes';

const log = pino({ level: process.env.LOG_LEVEL || 'info', transport: { target: 'pino-pretty' } });

/**
 * Estado das instancias em memoria.
 * Chave = nome da instancia; valor = { sock, estado, qr, pairingCode }
 */
const instancias = new Map();

// ---------------------------------------------------------------------------
// Ciclo de vida da conexao
// ---------------------------------------------------------------------------

/**
 * Cria (ou recupera) o socket de uma instancia e mantem seu estado atualizado.
 * Baileys emite o QR em connection.update; guardamos como PNG data URI para
 * o painel exibir direto em <img src>.
 */
async function iniciarInstancia(nome, numeroParaPareamento) {
  let atual = instancias.get(nome);

  // Ja existe socket vivo: nao recria, so devolve o estado corrente.
  if (atual?.sock && atual.estado !== 'close') {
    if (numeroParaPareamento && !atual.pairingCode && atual.estado !== 'open') {
      await solicitarCodigo(atual, numeroParaPareamento);
    }
    return atual;
  }

  const { state, saveCreds } = await useMultiFileAuthState(`${PASTA_SESSAO}/${nome}`);
  const { version } = await fetchLatestBaileysVersion();

  const sock = makeWASocket({
    version,
    auth: state,
    // Pareamento por codigo dispensa a impressao do QR no terminal.
    printQRInTerminal: false,
    logger: pino({ level: 'silent' }),
    browser: ['Evangelho do Dia', 'Chrome', '1.0.0'],
  });

  const registro = { sock, estado: 'connecting', qr: null, pairingCode: null, nome };
  instancias.set(nome, registro);

  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;

    if (qr) {
      // Converte o payload cru do QR em imagem para o painel.
      registro.qr = await qrcode.toDataURL(qr, { margin: 1, width: 320 });
      registro.estado = 'connecting';
      log.info(`[${nome}] QR code gerado`);
    }

    if (connection === 'open') {
      registro.estado = 'open';
      registro.qr = null;
      registro.pairingCode = null;
      log.info(`[${nome}] conectado como ${sock.user?.id}`);
    }

    if (connection === 'close') {
      const motivo = new Boom(lastDisconnect?.error)?.output?.statusCode;
      registro.estado = 'close';
      log.warn(`[${nome}] conexao encerrada (codigo ${motivo})`);

      // Sessao invalidada pelo celular: limpa para exigir novo pareamento.
      if (motivo === DisconnectReason.loggedOut) {
        instancias.delete(nome);
        return;
      }
      // Qualquer outro motivo e transitorio — reconecta.
      setTimeout(() => iniciarInstancia(nome).catch(e => log.error(e.message)), 3000);
    }
  });

  if (numeroParaPareamento) {
    await solicitarCodigo(registro, numeroParaPareamento);
  }

  return registro;
}

/**
 * Pede o codigo de pareamento de 8 caracteres.
 * Exige aguardar o socket abrir o canal antes de solicitar.
 */
async function solicitarCodigo(registro, numero) {
  if (registro.sock.authState.creds.registered) {
    return;
  }
  await new Promise(resolve => setTimeout(resolve, 3000));
  try {
    const codigo = await registro.sock.requestPairingCode(numero.replace(/\D/g, ''));
    registro.pairingCode = codigo;
    log.info(`[${registro.nome}] codigo de pareamento: ${codigo}`);
  } catch (e) {
    log.error(`[${registro.nome}] falha ao gerar codigo de pareamento: ${e.message}`);
  }
}

// ---------------------------------------------------------------------------
// HTTP
// ---------------------------------------------------------------------------

const app = express();
app.use(express.json());

/** Autenticacao por header, no mesmo formato da Evolution API. */
app.use((req, res, next) => {
  if (API_KEY && req.get('apikey') !== API_KEY) {
    return res.status(401).json({ message: 'apikey invalida' });
  }
  next();
});

/** Lista instancias conhecidas — o painel usa para saber se precisa criar. */
app.get('/instance/fetchInstances', (req, res) => {
  const nome = req.query.instanceName;
  const lista = [...instancias.keys()]
    .filter(k => !nome || k === nome)
    .map(k => ({ name: k, connectionStatus: instancias.get(k).estado }));
  res.json(lista);
});

/** Cria a instancia e ja inicia o pareamento. */
app.post('/instance/create', async (req, res, next) => {
  try {
    const { instanceName, number } = req.body || {};
    if (!instanceName) {
      return res.status(400).json({ message: 'instanceName obrigatorio' });
    }
    await iniciarInstancia(instanceName, number);
    res.status(201).json({ instance: { instanceName, status: 'created' } });
  } catch (e) {
    next(e);
  }
});

/**
 * Devolve o material de pareamento.
 * Aguarda alguns instantes porque o QR chega de forma assincrona do WhatsApp.
 */
app.get('/instance/connect/:instancia', async (req, res, next) => {
  try {
    const nome = req.params.instancia;
    const numero = req.query.number;
    const registro = await iniciarInstancia(nome, numero);

    // Espera ate 15s pelo QR ou pelo codigo, checando a cada 500ms.
    for (let i = 0; i < 30; i++) {
      if (registro.estado === 'open' || registro.qr || registro.pairingCode) break;
      await new Promise(r => setTimeout(r, 500));
    }

    res.json({
      base64: registro.qr || '',
      pairingCode: registro.pairingCode || '',
      code: registro.qr ? 'qr' : '',
    });
  } catch (e) {
    next(e);
  }
});

/** Estado da sessao, consultado em polling pelo painel. */
app.get('/instance/connectionState/:instancia', (req, res) => {
  const registro = instancias.get(req.params.instancia);
  res.json({
    instance: {
      instanceName: req.params.instancia,
      state: registro?.estado === 'open' ? 'open' : (registro?.estado || 'close'),
    },
  });
});

/** Encerra a sessao no celular. */
app.delete('/instance/logout/:instancia', async (req, res, next) => {
  try {
    const registro = instancias.get(req.params.instancia);
    if (registro?.sock) {
      await registro.sock.logout().catch(() => {});
    }
    instancias.delete(req.params.instancia);
    res.json({ status: 'logout' });
  } catch (e) {
    next(e);
  }
});

/** Grupos em que a conta conectada participa. */
app.get('/group/fetchAllGroups/:instancia', async (req, res, next) => {
  try {
    const registro = instancias.get(req.params.instancia);
    if (registro?.estado !== 'open') {
      return res.status(400).json({ message: 'instancia nao conectada' });
    }

    const grupos = await registro.sock.groupFetchAllParticipating();
    const lista = Object.values(grupos).map(g => ({
      id: g.id,
      subject: g.subject,
      size: g.participants?.length || 0,
    }));
    res.json(lista);
  } catch (e) {
    next(e);
  }
});

/** Envio de texto — mesmo payload da Evolution API v2. */
app.post('/message/sendText/:instancia', async (req, res, next) => {
  try {
    const registro = instancias.get(req.params.instancia);
    if (registro?.estado !== 'open') {
      return res.status(400).json({ message: 'instancia nao conectada' });
    }

    const { number, text } = req.body || {};
    if (!number || !text) {
      return res.status(400).json({ message: 'number e text obrigatorios' });
    }

    // Grupos ja vem com @g.us; numeros puros precisam do sufixo de contato.
    const destino = number.includes('@') ? number : `${number.replace(/\D/g, '')}@s.whatsapp.net`;
    const enviada = await registro.sock.sendMessage(destino, { text });

    res.status(201).json({ key: enviada?.key, status: 'sent' });
  } catch (e) {
    next(e);
  }
});

/** Erro tratado em um lugar so, sem vazar stack para o cliente. */
app.use((erro, req, res, _next) => {
  log.error(`${req.method} ${req.path}: ${erro.message}`);
  res.status(500).json({ message: erro.message });
});

app.listen(PORTA, HOST, () => {
  log.info(`Bridge WhatsApp ouvindo em http://${HOST}:${PORTA}`);
  if (!API_KEY) {
    log.warn('API_KEY nao definida: qualquer processo local pode controlar o WhatsApp.');
  }
});
