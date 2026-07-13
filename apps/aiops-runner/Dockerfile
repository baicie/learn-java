# syntax=docker/dockerfile:1.7
#
# Runner 镜像：执行 ansible / webhook 步骤的隔离进程。
# 运行时需要 ansible-playbook 与 sshpass（向目标主机推送 playbook 时使用）。
# 选择 Ubuntu/Jammy 而不是 Alpine，因为 ansible 官方包对 Alpine 支持较弱。

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG NODE_IMAGE=node:22-bookworm-slim
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${NODE_IMAGE} AS node-runtime

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

# aiops-persistence 在 generate-sources 阶段执行 Node 脚本生成 jOOQ DDL。
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node

COPY pom.xml ./
COPY apps ./apps
COPY modules ./modules
COPY contracts ./contracts
COPY scripts ./scripts

RUN --mount=type=cache,target=/root/.m2 \
    node --version \
    && mvn -pl apps/aiops-runner -am -DskipTests package \
    && APP_JAR="$(find /workspace/apps/aiops-runner/target -maxdepth 1 -type f -name 'aiops-runner-*.jar' ! -name '*.original' -print -quit)" \
    && test -n "$APP_JAR" \
    && rm -rf /tmp/boot-jar-check \
    && mkdir -p /tmp/boot-jar-check \
    && cd /tmp/boot-jar-check \
    && jar xf "$APP_JAR" META-INF/MANIFEST.MF \
    && grep -Fq "Main-Class: org.springframework.boot.loader.launch.JarLauncher" META-INF/MANIFEST.MF \
    && cp "$APP_JAR" /tmp/app.jar

FROM ${RUNTIME_IMAGE} AS runtime

ENV TZ=UTC
ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=private
ENV ANSIBLE_HOST_KEY_CHECKING=False
ENV PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ansible \
        sshpass \
        openssh-client \
        ca-certificates \
        curl \
        tini \
    && rm -rf /var/lib/apt/lists/*

# Jammy 是 Debian/Ubuntu 系，不能使用 Alpine 的 addgroup/adduser -S 参数。
RUN groupadd --system aiops \
    && useradd --system --gid aiops --home-dir /app --shell /usr/sbin/nologin aiops \
    && mkdir -p /app /var/log/aegisops \
    && chown -R aiops:aiops /app /var/log/aegisops

WORKDIR /app

COPY --from=build /tmp/app.jar /app/app.jar

USER aiops

EXPOSE 8092

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:8092/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
