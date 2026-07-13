# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG NODE_IMAGE=node:22-bookworm-slim
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine
ARG APP_MODULE=apps/aiops-server
ARG APP_NAME=aiops-server
ARG APP_PORT=8080

# aiops-persistence 在 generate-sources 阶段执行 Node 脚本生成 jOOQ DDL。
# 所有依赖 aiops-persistence 的 Java 应用镜像都必须具备 node 与 scripts/。
FROM ${NODE_IMAGE} AS node-runtime

FROM ${MAVEN_IMAGE} AS build

ARG APP_MODULE
ARG APP_NAME

WORKDIR /workspace

COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node

COPY pom.xml ./
COPY apps ./apps
COPY modules ./modules
COPY contracts ./contracts
COPY scripts ./scripts

RUN --mount=type=cache,target=/root/.m2 \
    node --version \
    && mvn -pl "${APP_MODULE}" -am -DskipTests package

FROM ${RUNTIME_IMAGE} AS runtime

ARG APP_MODULE
ARG APP_NAME
ARG APP_PORT

ENV TZ=UTC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=private
ENV APP_PORT="${APP_PORT}"

RUN addgroup -S aiops && adduser -S aiops -G aiops \
    && mkdir -p /app /var/log/aegisops \
    && chown -R aiops:aiops /app /var/log/aegisops

WORKDIR /app

COPY --from=build /workspace/${APP_MODULE}/target/*.jar /app/app.jar

USER aiops

EXPOSE ${APP_PORT}

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${APP_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
