# syntax=docker/dockerfile:1.7
#
# aiops-server 镜像：
#   1. portal-build : node:20 + pnpm 装 web/portal 依赖，build 出 web/portal/dist
#   2. server-build : maven 离线打 server fat-jar；portal 的 dist 复制到 static/portal
#   3. runtime      : eclipse-temurin 21-jre-alpine 跑 jar，暴露 8080
#
# 镜像 JDK 与 pom.xml 的 java.version=21 保持一致：
#   maven:3.9.9-eclipse-temurin-21 + eclipse-temurin:21-jre-alpine。
# 前端是 web/portal（shadcn-admin 派生），console 已废弃。

FROM node:20-bookworm-slim AS portal-build
WORKDIR /workspace

RUN corepack enable

# 复制 lockfile + 子项目 package.json，最大化缓存命中
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/portal/package.json web/portal/package.json

RUN pnpm install --frozen-lockfile

COPY web/portal web/portal
RUN pnpm --dir web/portal build


FROM maven:3.9.9-eclipse-temurin-21 AS server-build
WORKDIR /workspace

COPY pom.xml ./
COPY modules modules
COPY apps apps
COPY contracts contracts
COPY infra/checkstyle infra/checkstyle

# portal 静态产物塞进 server jar 的 static/portal
# 不放 static/ 根，因为 server 自己可能有 swagger / actuator 资源
COPY --from=portal-build /workspace/web/portal/dist apps/aiops-server/src/main/resources/static/portal

RUN mvn -pl apps/aiops-server -am package -DskipTests


FROM eclipse-temurin:21-jre-alpine AS runtime

ENV TZ=UTC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=private
ENV AIOPS_SERVER_PORT=8080

RUN addgroup -S aiops && adduser -S aiops -G aiops \
    && mkdir -p /app /var/log/aegisops \
    && chown -R aiops:aiops /app /var/log/aegisops

WORKDIR /app

COPY --from=server-build /workspace/apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar /app/app.jar

USER aiops

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:8080/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
