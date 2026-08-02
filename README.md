# AegisOps / FaultLens

AegisOps / FaultLens 是围绕 Incident 的可观测、智能排障与受控自动化平台。后端采用单一
Java 模块化单体 `aegisops-app`，外挂 Python Agent，并用独立 Runner 隔离高权限执行。它们是
三个运行时与权限边界，不是按业务域拆分的微服务。

## 技术栈

- Backend: Java 21 + Spring Boot 3.x + Spring Security + Flyway + JdbcTemplate
- Frontend: React + Vite + TypeScript + Tailwind CSS + TanStack Query
- Core Infra: PostgreSQL
- Optional Infra: Redis + VictoriaMetrics + MinIO + Zabbix
- Build: Maven multi-module + pnpm

## 本阶段交付

```txt
Maven 多模块项目
aegisops-app（Portal/API + Worker runtime）
aiops-agent（Python AI 工作流）
aiops-runner（可选高权限执行进程）
React 控制台
Docker Compose 基础环境
PostgreSQL / Redis / ClickHouse / VictoriaMetrics / MinIO
Flyway 数据库迁移
Spring Security + JWT
基础 RBAC
OpenAPI
系统健康检查
```

## 快速启动

### 默认诊断模式

```bash
bash deploy/install.sh
```

默认只启动：

```text
aegisops-app（内嵌 Portal + Worker runtime）
aiops-agent
PostgreSQL
```

Runner、Redis、MinIO、VictoriaMetrics 与内置 Zabbix 的 profile 命令见
`deploy/README.md`。

首次安装时直接启用 Runner：

```bash
bash deploy/install.sh --mode automation
```

### 源码开发

安装依赖：

```bash
npm install -g pnpm@10
pnpm install --frozen-lockfile
```

只启动 PostgreSQL：

```bash
docker compose -f infra/docker-compose.yml up -d postgres
```

源码开发使用本地开发配置；生产 Compose 的数据库密码、mTLS 证书与 Grant 密钥由安装器生成，
不要在源码开发命令中复用 `deploy/runtime` 私钥。

初始化后端模块：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn install -DskipTests
```

启动后端：

在仓库根目录执行：

```bash
# aegisops-app，主 API 与后台作业
mvn -pl apps/aiops-server -am spring-boot:run
```

Agent 与 Runner 仅在开发对应能力时启动。后台作业已经由
`modules/aiops-worker-runtime` 装入 `aegisops-app`，不存在独立 Worker 启动入口：

```bash
python -m aiops_agent.serve
mvn -pl apps/aiops-runner -am spring-boot:run
```

启动 Portal：

```bash
pnpm -C web/portal dev
```

### 5. 默认账号

```txt
username: admin
password: admin123
```

## API

- Server: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

## Core 验收

```txt
诊断模式三容器可以一键启动
后端可以启动并自动迁移数据库
首次启动自动创建默认租户与 admin 用户
可以登录
可以访问 /api/auth/me
可以查看租户、用户、角色
可以查看空 Dashboard
可以访问 OpenAPI 文档
App 同一 JVM 运行后台调度与 Outbox
App/Agent 使用 mTLS + 短期 Diagnosis Grant，不要求 Keycloak/JWKS
Agent 与 Runner 均不暴露宿主机业务端口
```

## 推荐验证命令

后端：

```bash
mvn -pl apps/aiops-server -am test
mvn -pl modules/aiops-worker-runtime -am test
mvn -pl apps/aiops-runner -am test
```

Portal：

```bash
pnpm -C web/portal build
```
