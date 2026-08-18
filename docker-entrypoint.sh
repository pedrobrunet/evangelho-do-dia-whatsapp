#!/usr/bin/env bash
#
# Sobe os dois processos do container: o bridge Node e o painel Spring Boot.
#
# Se qualquer um dos dois morrer, o script encerra — a plataforma reinicia o
# container inteiro. Manter meio sistema no ar seria pior: o painel sem o
# bridge aceita agendamentos que nunca seriam entregues.

set -euo pipefail

DIR_DADOS="${DATA_DIR:-/data}"
mkdir -p "${DIR_DADOS}/sessoes"

echo "[entrypoint] dados persistidos em ${DIR_DADOS}"

# --- bridge WhatsApp -------------------------------------------------------
# HOST fixo em 127.0.0.1: o bridge nunca deve ser alcancavel de fora do
# container, pois quem fala com ele controla o WhatsApp pareado.
HOST=127.0.0.1 \
PORT=8080 \
API_KEY="${WHATSAPP_API_KEY:-}" \
SESSION_DIR="${DIR_DADOS}/sessoes" \
    node whatsapp-bridge/server.js &
PID_BRIDGE=$!
echo "[entrypoint] bridge iniciado (pid ${PID_BRIDGE})"

# --- painel Spring Boot ----------------------------------------------------
# shellcheck disable=SC2086
java ${JAVA_OPTS:-} -jar painel.jar &
PID_PAINEL=$!
echo "[entrypoint] painel iniciado (pid ${PID_PAINEL})"

encerrar() {
    echo "[entrypoint] encerrando..."
    kill "${PID_BRIDGE}" "${PID_PAINEL}" 2>/dev/null || true
}
trap encerrar EXIT INT TERM

# Retorna assim que o primeiro dos dois terminar.
wait -n "${PID_BRIDGE}" "${PID_PAINEL}"
CODIGO=$?
echo "[entrypoint] um dos processos terminou (codigo ${CODIGO}); encerrando o container"
exit "${CODIGO}"
