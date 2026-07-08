# syntax=docker/dockerfile:1.7
#
# Runner 镜像：执行 ansible / webhook 步骤的隔离进程。
# 运行时需要 ansible-playbook 与 sshpass（向目标主机推送 playbook 时使用）。
# 选择 debian-slim 而不是 alpine，因为 ansible 官方包对 alpine 支持较弱。

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

COPY pom.xml ./
COPY apps ./apps
COPY modules ./modules

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl apps/aiops-runner -am -DskipTests package

FROM ${RUNTIME_IMAGE} AS runtime

ENV TZ=UTC
ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=private
# 关闭 ANSIBLE_HOST_KEY_CHECKING：runner 频繁连接新目标，不希望交互式确认 host key
ENV ANSIBLE_HOST_KEY_CHECKING=False
# 关闭 PYTHONUNBUFFERED 不是为了 python，而是某些 ansible 模块依赖 tty 行为
ENV PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ansible \
        sshpass \
        openssh-client \
        ca-certificates \
        tini \
    && rm -rf /var/lib/apt/lists/*

RUN addgroup -S aiops && adduser -S aiops -G aiops \
    && mkdir -p /app /var/log/aegisops \
    && chown -R aiops:aiops /app /var/log/aegisops

WORKDIR /app

COPY --from=build /workspace/apps/aiops-runner/target/aiops-runner-*.jar /app/app.jar

USER aiops

EXPOSE 8092

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:8092/actuator/health" || exit 1

# tini 负责 reap zombie 进程，避免 ansible 偶发孤儿进程拖死 runner
ENTRYPOINT ["/usr/bin/tini", "--", "sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
