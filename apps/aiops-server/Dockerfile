# syntax=docker/dockerfile:1.7

FROM node:20-bookworm-slim AS console-build
WORKDIR /workspace

RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/console/package.json web/console/package.json

RUN pnpm install --frozen-lockfile

COPY web/console web/console
RUN pnpm --dir web/console build


FROM maven:3.9-eclipse-temurin-17 AS server-build
WORKDIR /workspace

COPY pom.xml ./
COPY modules modules
COPY apps apps
COPY contracts contracts
COPY infra/checkstyle infra/checkstyle

COPY --from=console-build /workspace/web/console/dist apps/aiops-server/src/main/resources/static

RUN mvn -pl apps/aiops-server -am package -DskipTests


FROM eclipse-temurin:17-jre
WORKDIR /app

ENV JAVA_OPTS=""
ENV AIOPS_SERVER_PORT=8080

COPY --from=server-build /workspace/apps/aiops-server/target/aiops-server-0.1.0-SNAPSHOT.jar /app/aiops-server.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/aiops-server.jar"]
