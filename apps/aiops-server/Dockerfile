# syntax=docker/dockerfile:1.7
#
# aiops-server 镜像：
#   1. portal-build：固定 Node/pnpm 版本，独立构建 React Portal；
#   2. build：Maven/JDK 21 构建 Java，并把 Portal dist 作为静态资源打包；
#   3. runtime：JRE 21 运行最终 fat jar。
#
# 前端构建不再由 Maven 递归启动 web/portal/pom.xml，避免重复下载 Node、
# 嵌套 Maven 日志难定位以及前端 POM 配置漂移。

ARG NODE_IMAGE=node:22-bookworm-slim
ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${NODE_IMAGE} AS portal-build

WORKDIR /workspace

RUN npm install --global pnpm@10.33.4

# 先只复制依赖清单，提高源码变化时的依赖层命中率。
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/portal/package.json web/portal/package.json

RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    pnpm config set store-dir /pnpm/store \
    && pnpm install --frozen-lockfile --filter aegisops-portal...

COPY web/portal web/portal

RUN pnpm --filter aegisops-portal build

FROM ${MAVEN_IMAGE} AS build

WORKDIR /workspace

# aiops-persistence 在 generate-sources 阶段使用 Node 预处理 Flyway DDL。
COPY --from=portal-build /usr/local/bin/node /usr/local/bin/node

COPY pom.xml ./
COPY modules modules
COPY apps apps
COPY contracts contracts
COPY infra/checkstyle infra/checkstyle
COPY scripts scripts
COPY --from=portal-build /workspace/web/portal/dist web/portal/dist

# with-portal 只负责把已构建的 dist 拷进 static；显式跳过其旧的嵌套前端构建。
RUN --mount=type=cache,target=/root/.m2 \
    node --version \
    && mvn -B -ntp \
       -pl apps/aiops-server \
       -am \
       package \
       -DskipTests \
       -P with-portal \
       -Daiops.portal.skipBuild=true

FROM ${RUNTIME_IMAGE} AS runtime

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
