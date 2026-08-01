# AegisOps / FaultLens

AegisOps / FaultLens 是围绕 Incident 的可观测、智能排障与受控自动化平台。后端采用模块化
单体代码库，并按职责隔离 Server、Worker、Runner 进程；Python Agent 为可选能力。

## 技术栈

- Backend: Java 21 + Spring Boot 3.x + Spring Security + Flyway + JdbcTemplate
- Frontend: React + Vite + TypeScript + Tailwind CSS + TanStack Query
- Core Infra: PostgreSQL
- Optional Infra: Redis + VictoriaMetrics + MinIO + Zabbix
- Build: Maven multi-module + pnpm

## 本阶段交付

```txt
Maven 多模块项目
aiops-server / aiops-worker / aiops-runner 三个应用
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

### 默认 Core

```bash
docker compose -f deploy/docker-compose.core.yml up --build -d --wait
```

默认只启动：

```text
aiops-server（内嵌 Portal）
aiops-worker
PostgreSQL
```

AI、Runner、Redis、MinIO、VictoriaMetrics 与内置 Zabbix 的 profile 命令见
`deploy/README.md`。

### 源码开发

安装依赖：

```bash
npm install -g pnpm@10
pnpm install --frozen-lockfile
```

只启动 PostgreSQL：

```bash
docker compose -f deploy/docker-compose.core.yml up -d postgres
```

初始化后端模块：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn install -DskipTests
```

启动后端：

在仓库根目录执行：

```bash
# aiops-server，主 API 服务
mvn -pl apps/aiops-server -am spring-boot:run
```

Worker 可独立启动；Runner 仅在开发自动化能力时启动：

```bash
mvn -pl apps/aiops-worker -am spring-boot:run
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
Core 三容器可以一键启动
后端可以启动并自动迁移数据库
首次启动自动创建默认租户与 admin 用户
可以登录
可以访问 /api/auth/me
可以查看租户、用户、角色
可以查看空 Dashboard
可以访问 OpenAPI 文档
Worker 可以独立启动并暴露健康检查
Agent 关闭时不要求 Keycloak/JWKS，也不暴露内部 Agent API
```

## 推荐验证命令

后端：

```bash
mvn -pl apps/aiops-server -am test
mvn -pl apps/aiops-worker -am test
mvn -pl apps/aiops-runner -am test
```

Portal：

```bash
pnpm -C web/portal build
```
