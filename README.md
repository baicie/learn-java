# AegisOps / FaultLens Phase0

Phase0 是 AIOps MVP 的工程地基，目标是先把可持续迭代的工程骨架、基础设施、登录权限、租户/RBAC、审计、OpenAPI 与前端控制台跑通。

## 技术栈

- Backend: Java 21 + Spring Boot 3.x + Spring Security + Flyway + JdbcTemplate
- Frontend: React + Vite + TypeScript + Tailwind CSS + TanStack Query
- Infra: PostgreSQL + Redis + ClickHouse + VictoriaMetrics + MinIO
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

### 1. 启动基础设施

```bash
docker compose -f infra/docker-compose.yml up -d
```

### 2. 启动后端

```bash
mvn -pl apps/aiops-server -am spring-boot:run
```

Worker / Runner 可分别启动：

```bash
mvn -pl apps/aiops-worker -am spring-boot:run
mvn -pl apps/aiops-runner -am spring-boot:run
```

### 3. 启动前端

```bash
cd web/console
pnpm install
pnpm dev
```

### 4. 默认账号

```txt
username: admin
password: admin123
```

## API

- Server: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health: http://localhost:8080/actuator/health

## Phase0 验收

```txt
本地一键启动基础设施
后端可以启动并自动迁移数据库
首次启动自动创建默认租户与 admin 用户
可以登录
可以访问 /api/auth/me
可以查看租户、用户、角色
可以查看空 Dashboard
可以访问 OpenAPI 文档
Worker / Runner 可以独立启动并暴露健康检查
```
