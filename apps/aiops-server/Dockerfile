# syntax=docker/dockerfile:1.7
#
# aiops-server 镜像：
#   1. build     : eclipse-temurin:21-jdk-jammy + maven(从 maven 镜像 COPY)
#                  + node 20（仅给 aiops-persistence 的 exec-maven-plugin
#                  在 generate-sources 阶段跑 `node scripts/prepare-jooq-ddl.mjs` 用）。
#                  前端打包由 server 的 pom 反向调用 web/portal/pom.xml 完成
#                  （-P with-portal），不再在 docker 里直接调 pnpm。
#   2. runtime   : eclipse-temurin 21-jre-alpine 跑 jar，暴露 8080
#
# 镜像 JDK 与 pom.xml 的 java.version=21 保持一致。

FROM maven:3.9.9-eclipse-temurin-21 AS maven-base


FROM eclipse-temurin:21-jdk-jammy AS build

ENV DEBIAN_FRONTEND=noninteractive \
    NODE_VERSION=20.20.2 \
    MAVEN_HOME=/usr/share/maven

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        git \
        xz-utils \
    && curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
        | tar -xJ -C /usr/local --strip-components=1 --no-same-owner \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 从 maven 官方镜像拷贝 maven 二进制，避免 archive.apache.org 下载
COPY --from=maven-base ${MAVEN_HOME} ${MAVEN_HOME}
RUN ln -s ${MAVEN_HOME}/bin/mvn /usr/local/bin/mvn

WORKDIR /workspace

# server 构建：pom 反向触发 web/portal/pom.xml，由它自己装 node/pnpm + pnpm build，
# 产物 web/portal/dist 被 server pom 的 maven-resources-plugin 在 process-resources
# 阶段拷到 static/。
COPY pom.xml ./
COPY modules modules
COPY apps apps
COPY contracts contracts
COPY infra/checkstyle infra/checkstyle
COPY web web
COPY scripts scripts

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl apps/aiops-server -am package -DskipTests -P with-portal


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