# Imagem unica com os dois processos: o painel Spring Boot e o bridge Node.
#
# Poderiam ser dois servicos, mas o bridge nunca deve ficar exposto — quem o
# alcanca controla o WhatsApp pareado. Mantendo-o no mesmo container, ele
# escuta apenas em 127.0.0.1 e so o painel o enxerga.

# ---------------------------------------------------------------------------
# Estagio 1 — compila o painel Java
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build-java
WORKDIR /build

# As dependencias mudam menos que o codigo: copiar o pom antes aproveita
# o cache de camadas nos builds seguintes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------------------------------------------------------------------------
# Estagio 2 — dependencias do bridge Node
# ---------------------------------------------------------------------------
FROM node:22-bookworm-slim AS build-node
WORKDIR /bridge

COPY whatsapp-bridge/package.json whatsapp-bridge/package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts

# ---------------------------------------------------------------------------
# Estagio 3 — runtime
# ---------------------------------------------------------------------------
FROM node:22-bookworm-slim

# O painel compila para Java 17, entao o JRE 17 do Debian basta —
# bem menor que instalar um JDK completo.
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=build-java /build/target/*.jar ./painel.jar
COPY --from=build-node /bridge/node_modules ./whatsapp-bridge/node_modules
COPY whatsapp-bridge/package.json whatsapp-bridge/server.js ./whatsapp-bridge/
COPY docker-entrypoint.sh .
# Remove CR de checkouts feitos no Windows: com CRLF o shebang falha no Linux.
RUN tr -d '\r' < docker-entrypoint.sh > /tmp/e.sh && mv /tmp/e.sh docker-entrypoint.sh && chmod +x docker-entrypoint.sh

# Diretorio dos dados que precisam sobreviver a um redeploy:
# credenciais da sessao do WhatsApp e os agendamentos.
# Monte um volume da plataforma aqui, senao o pareamento se perde a cada deploy.
ENV DATA_DIR=/data
VOLUME ["/data"]

ENV APP_AGENDAMENTOS_FILE=/data/agendamentos.json \
    WHATSAPP_PROVIDER=EVOLUTION \
    WHATSAPP_API_URL=http://127.0.0.1:8080 \
    WHATSAPP_INSTANCE=evangelho \
    WHATSAPP_SIMULAR=false \
    JAVA_OPTS="-XX:MaxRAMPercentage=70"

# A plataforma injeta PORT; 8081 e o padrao para execucao local.
EXPOSE 8081

# O bridge fica em 127.0.0.1:8080, alcancavel apenas de dentro do container.
CMD ["./docker-entrypoint.sh"]
