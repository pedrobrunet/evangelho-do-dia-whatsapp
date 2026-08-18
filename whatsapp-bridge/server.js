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
import { rm } from 'node:fs/promises';

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

/** Teto de reconexoes seguidas, para nao entrar em loop quando a sessao morre. */
const MAX_RECONEXOES = 5;

const log = pino({ level: process.env.LOG_LEVEL || 'info', transport: { target: 'pino-pretty' } });

/**
 * Estado das instancias em memoria.
 * Chave = nome; valor = { sock, estado, qr, pairingCode, modo, reconexoes, iniciando }
 */
const instancias = new Map();

// ---------------------------------------------------------------------------
// Ciclo de vida da conexao
// ---------------------------------------------------------------------------

/**
 * Cria (ou recupera) o socket de uma instancia.
 *
 * `modo` decide o material de pareamento e nao muda depois que o socket sobe:
 *  - 'qr'      → o WhatsApp emite QR codes sucessivos, renovados sozinhos
 *  - 'codigo'  → um unico codigo de 8 caracteres, que NAO pode ser regerado
 *                enquanto o usuario o digita (regerar invalida o anterior)
 *
 * Chamadas concorrentes compartilham a mesma promessa de inicializacao —
 * sem isso, o polling do painel criaria varios sockets para a mesma instancia.
 */
async function iniciarInstancia(nome, numeroParaPareamento) {
  const modoDesejado = numeroParaPareamento ? 'codigo' : 'qr';
  const atual = instancias.get(nome);

  // Inicializacao em andamento: aguarda a mesma, em vez de abrir outra.
  if (atual?.iniciando) {
    await atual.iniciando;
    return instancias.get(nome);
  }

  if (atual?.sock && atual.estado !== 'close') {
    // Ja conectado: jamais gerar novo material de pareamento — derruba a sessao.
    if (atual.estado === 'open') {
      return atual;
    }
    // Mesmo modo: devolve o material vigente (o codigo precisa permanecer estavel).
    if (atual.modo === modoDesejado) {
      return atual;
    }
    // Troca de modo (QR → codigo, ou vice-versa) exige socket novo.
    log.info(`[${nome}] alternando pareamento de ${atual.modo} para ${modoDesejado}`);
    await encerrarSocket(atual);
  }

  const registro = {
    sock: null,
    estado: 'connecting',
    qr: null,
    pairingCode: null,
    modo: modoDesejado,
    reconexoes: atual?.reconexoes || 0,
    nome,
    iniciando: null,
  };
  instancias.set(nome, registro);

  registro.iniciando = abrirSocket(registro, numeroParaPareamento);
  try {
    await registro.iniciando;
  } finally {
    registro.iniciando = null;
  }
  return registro;
}

async function abrirSocket(registro, numeroParaPareamento) {
  const nome = registro.nome;
  const { state, saveCreds } = await useMultiFileAuthState(`${PASTA_SESSAO}/${nome}`);
  const { version } = await fetchLatestBaileysVersion();

  const sock = makeWASocket({
    version,
    auth: state,
    printQRInTerminal: false,
    logger: pino({ level: 'silent' }),
    browser: ['Evangelho do Dia', 'Chrome', '1.0.0'],
  });

  registro.sock = sock;
  sock.ev.on('creds.update', saveCreds);

  sock.ev.on('connection.update', async (update) => {
    const { connection, lastDisconnect, qr } = update;

    // No modo codigo o QR e ignorado: exibir os dois confunde e nao ha necessidade.
    if (qr && registro.modo === 'qr') {
      registro.qr = await qrcode.toDataURL(qr, { margin: 1, width: 320 });
      registro.estado = 'connecting';
      log.info(`[${nome}] QR code gerado`);
    }

    if (connection === 'open') {
      registro.estado = 'open';
      registro.qr = null;
      registro.pairingCode = null;
      registro.reconexoes = 0;
      log.info(`[${nome}] conectado como ${sock.user?.id}`);
    }

    if (connection === 'close') {
      // O statusCode vive no proprio erro (Boom). Envolver em new Boom() o
      // substituiria por 500 e mascararia justamente o 401 de sessao invalida.
      const motivo = lastDisconnect?.error?.output?.statusCode;
      registro.estado = 'close';
      log.warn(`[${nome}] conexao encerrada (codigo ${motivo})`);

      // 401: credenciais invalidas ou sessao removida no celular.
      // Reconectar aqui gera loop infinito — limpa e exige novo pareamento.
      if (motivo === DisconnectReason.loggedOut || motivo === 401) {
        log.warn(`[${nome}] sessao invalidada; limpando credenciais`);
        instancias.delete(nome);
        await limparSessao(nome);
        return;
      }

      if (registro.reconexoes >= MAX_RECONEXOES) {
        log.error(`[${nome}] limite de reconexoes atingido; desistindo`);
        instancias.delete(nome);
        return;
      }

      // 515 (restartRequired) e esperado logo apos o pareamento por codigo.
      registro.reconexoes += 1;
      setTimeout(() => {
        iniciarInstancia(nome, registro.modo === 'codigo' ? numeroParaPareamento : null)
          .catch(e => log.error(`[${nome}] falha ao reconectar: ${e.message}`));
      }, 3000);
    }
  });

  if (registro.modo === 'codigo') {
    await solicitarCodigo(registro, numeroParaPareamento);
  }
}

/**
 * Pede o codigo de pareamento de 8 caracteres — uma unica vez por socket.
 *
 * Cada chamada invalida o codigo anterior, entao repetir isso enquanto o
 * usuario digita e o que faz o codigo "nao pegar".
 */
async function solicitarCodigo(registro, numero) {
  if (registro.pairingCode || registro.sock.authState.creds.registered) {
    return;
  }

  // O socket precisa de alguns instantes para abrir o canal antes do pedido.
  await new Promise(resolve => setTimeout(resolve, 3000));

  // Revalida apos a espera: a conexao pode ter aberto nesse intervalo, e pedir
  // codigo sobre uma sessao ativa a derruba.
  if (registro.estado === 'open' || registro.sock.authState.creds.registered) {
    log.info(`[${registro.nome}] ja conectado; codigo de pareamento dispensado`);
    return;
  }

  try {
    const codigo = await registro.sock.requestPairingCode(numero.replace(/\D/g, ''));
    registro.pairingCode = codigo;
    log.info(`[${registro.nome}] codigo de pareamento: ${codigo}`);
  } catch (e) {
    log.error(`[${registro.nome}] falha ao gerar codigo de pareamento: ${e.message}`);
  }
}

async function encerrarSocket(registro) {
  try {
    registro.sock?.ev?.removeAllListeners('connection.update');
    registro.sock?.end?.();
  } catch {
    // Encerramento e melhor-esforco: um socket ja morto nao impede o novo.
  }
}

async function limparSessao(nome) {
  await rm(`${PASTA_SESSAO}/${nome}`, { recursive: true, force: true }).catch(() => {});
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
 * Devolve o material de pareamento vigente.
 *
 * Idempotente de proposito: chamado repetidamente pelo painel, devolve o mesmo
 * codigo enquanto ele for valido, e apenas o QR se renova (quem o renova e o
 * proprio WhatsApp, a cada ~20s).
 */
app.get('/instance/connect/:instancia', async (req, res, next) => {
  try {
    const nome = req.params.instancia;
    const numero = req.query.number;
    const registro = await iniciarInstancia(nome, numero);

    // Espera ate 15s pelo material, checando a cada 500ms.
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

/** Encerra a sessao no celular e limpa as credenciais locais. */
app.delete('/instance/logout/:instancia', async (req, res, next) => {
  try {
    const nome = req.params.instancia;
    const registro = instancias.get(nome);
    if (registro?.sock) {
      await registro.sock.logout().catch(() => {});
      await encerrarSocket(registro);
    }
    instancias.delete(nome);
    await limparSessao(nome);
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
