# syntax=docker/dockerfile:1.7
#
# aiops-server 镜像：
#   1. build     : eclipse-temurin:21-jdk-bookworm + node + maven(从 maven 镜像 COPY)
#                  先 pnpm 出 portal/dist，再用 mvn 打 server fat-jar
#   2. runtime   : eclipse-temurin 21-jre-alpine 跑 jar，暴露 8080
#
# 镜像 JDK 与 pom.xml 的 java.version=21 保持一致。
# build 阶段必须同时提供 node 和 maven，因为：
#   - portal-build 阶段需要 node + pnpm
#   - aiops-persistence 的 exec-maven-plugin 在 generate-sources 阶段
#     调 `node scripts/prepare-jooq-ddl.mjs` 做 jOOQ DDL 预处理
#   - aiops-server 的 exec-maven-plugin 在 generate-resources 阶段调 pnpm
# 基础镜像组合：
#   - temurin:21-jdk-bookworm  -> JDK 21，免 apt 装 JDK 21
#   - maven:3.9.9-eclipse-temurin-21  -> maven 二进制，免 archive.apache.org 下载
#   - nodejs.org tarball      -> node 20，免 corepack 拉取器离线不稳
#
# 前端是 web/portal（shadcn-admin 派生），console 已废弃。

FROM maven:3.9.9-eclipse-temurin-21 AS maven-base


FROM eclipse-temurin:21-jdk-jammy AS build

ENV DEBIAN_FRONTEND=noninteractive \
    NODE_VERSION=20.18.0 \
    MAVEN_HOME=/usr/share/maven

# corepack 0.31（node 20.18 内置）尚未包含 pnpm 较新的签名公钥
# 用 npm 把 corepack 升到 0.34+，pnpm 版本由 package.json 的 packageManager 字段锁定
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        xz-utils \
    && curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
        | tar -xJ -C /usr/local --strip-components=1 --no-same-owner \
    && npm install -g corepack@0.34.1 \
    && corepack enable \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 从 maven 官方镜像拷贝 maven 二进制，避免 archive.apache.org 下载
COPY --from=maven-base ${MAVEN_HOME} ${MAVEN_HOME}
RUN ln -s ${MAVEN_HOME}/bin/mvn /usr/local/bin/mvn

WORKDIR /workspace

# ---- portal：先用 lockfile + package.json 最大化 pnpm 缓存命中 ----
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/portal/package.json web/portal/package.json
RUN pnpm install --frozen-lockfile

COPY web/portal web/portal
RUN pnpm --dir web/portal build

# ---- server：把 portal 静态产物塞进 server 的 static/portal，再让 mvn 打 fat-jar ----
# 不放 static/ 根，因为 server 自己可能有 swagger / actuator 资源
COPY pom.xml ./
COPY modules modules
COPY apps apps
COPY contracts contracts
COPY infra/checkstyle infra/checkstyle

RUN mkdir -p apps/aiops-server/src/main/resources/static/portal \
    && cp -R web/portal/dist/. apps/aiops-server/src/main/resources/static/portal/

# 把 m2 仓放到 buildkit cache，避免每次拉依赖
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl apps/aiops-server -am package -DskipTests


FROM eclipse-temurin:21-jre-alpine AS runtime

ENV TZ=UTC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=private
ENV AIOPS_SERVER_PORT=8080

RUN addgroup -S aiops && adduser -S aiops -G aiops \
    && mkdir -p /app /var/log/aegisops \
    && chown -R aiops:aiops /app /var/log/aegisops

WORKDIR /app

COPY --from=build /workspace/apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar /app/app.jar

USER aiops

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- "http://127.0.0.1:8080/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]