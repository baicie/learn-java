# scripts/start.ts

AegisOps 一键启动脚本：拉起 Docker Compose 基础设施、构建并启动三个后端
应用（aiops-server / aiops-worker / aiops-runner），提供健康检查与日志追踪。

**单一职责**：本地开发环境的进程编排。不读 docs、不写 docs、不改数据库结构。

## 用法

```bash
tsx scripts/start.ts <command>
```

未传子命令或传 `all` 时按顺序执行：infra → build → 三个 backend app。

## 子命令

| 子命令        | 作用                                                                               | 副作用                           |
| ------------- | ---------------------------------------------------------------------------------- | -------------------------------- |
| `all`（默认） | infra + build + start 三个后端                                                     | 启动 Docker volumes、Java 子进程 |
| `dev`         | infra + server + worker + Portal；不启动 runner                                    | 启动本地联调所需进程             |
| `infra`       | 仅 `docker compose up -d`（PostgreSQL、Redis、ClickHouse、VictoriaMetrics、MinIO） | 启动容器                         |
| `backend`     | 仅 `mvn package` + 启动三个后端                                                    | 启动 Java 子进程                 |
| `frontend`    | 仅启动 web/console dev server                                                      | 启动 Vite dev server             |
| `status`      | 打印系统要求检查（Java / Maven / Docker / pnpm）+ 容器健康                         | 只读                             |
| `stop`        | 停止三个后端 Java 子进程                                                           | 写 PID 文件 `apps/<app>/.pid`    |
| `infra-stop`  | `docker compose down`（保留 volumes）                                              | 停止容器                         |
| `clean`       | stop + `docker compose down -v`（**DESTROYS DATA**）                               | 删除所有 volumes                 |
| `logs <app>`  | tail `apps/<app>/logs/console.log`                                                 | 只读                             |
| `help`        | 打印 usage                                                                         | 只读                             |

`app` 可选值：`server`（port 8080）/ `worker`（本地 port 8091）/ `runner`（port 8082）。

## 启动顺序与超时

```txt
1. 基础设施 (Docker Compose)        -- 阻塞到容器 healthcheck 通过
2. Maven 构建（一次性）             -- mvn -q -DskipTests package
3. 三个后端 (server → worker → runner) -- 每个用 start-stop-daemon 等价物后台启动
```

默认无总超时；infra 启动依赖 Docker daemon，backend 启动依赖 PostgreSQL 端口可达。
若 30 秒内 Postgres 不可达，Java 进程会 fast-fail，可通过 `logs server` 排查。

## 边界（不做什么）

- **不读 docs / 不写 docs**：与 `scripts/docs.ts` 完全独立
- **不修改数据库 schema**：所有迁移通过 Flyway 在 backend 启动时跑
- **不调用 LLM / 不调 ansible**：纯编排
- **不依赖 docs.ts**：两个脚本独立维护，互不 import

## 与 scripts/ci/ 的关系

- `scripts/ci/*` 是 CI 流水线（verify-local.sh、docs.sh 等）—— 用于 GitHub Actions / 本地
- `scripts/start.ts` 是本地开发的快速入口 —— 给人用的，不是给 CI 用的
- 两者不重复：ci 强调守门，start 强调编排

## 故障排查

| 现象                         | 原因                         | 修复                         |
| ---------------------------- | ---------------------------- | ---------------------------- |
| 容器起不来                   | Docker daemon 未运行         | `docker info`                |
| Maven 构建失败               | Java 版本不符                | SKILL §4 要求 JDK 21         |
| 后端起不来                   | 8080/8091/8082 端口被占      | `netstat -ano \| findstr :8080` |
| `clean` 后 Postgres 数据丢失 | 这是设计行为                 | 不在生产环境用 clean         |
| 找不到 tsx                   | 未安装                       | `pnpm install`               |
