# infra — 本地开发基础设施

本目录仅承载**开发者本机与单机调试**所使用的容器编排与样例配置。

## 边界

```text
infra/    ── docker-compose + env.example（本地起 PG/Redis/CH/VM/MinIO/Zabbix）
deploy/   ── Helm chart + Dockerfile + 离线包（生产 / 预发部署）

两个目录职责严格分离：
- 本地开发：docker compose --env-file infra/.env up
- 生产部署：helm install / kubectl apply（走 deploy/）
```

## 包含内容

- `docker-compose.yml`：PostgreSQL / Redis / ClickHouse / VictoriaMetrics / MinIO / Zabbix 的本地编排
- App、Agent 与 Runner 不放入 `infra/`；完整诊断或自动化拓扑使用 `deploy/install.sh`
- `env.example`：环境变量样例（**所有值为占位符或样例值**，禁止提交真实密钥）
- `.env`（**本地维护，不入库**）：开发者本机的真实环境变量
- `checkstyle/checkstyle.xml`：后端 Checkstyle 配置，被根 `pom.xml` 通过 `${maven.multiModuleProjectDirectory}/infra/checkstyle/checkstyle.xml` 引用
- `SECRETS.md`：本地 / CI / 部署三类场景的密钥管理流程

## 使用

```bash
# 1. 首次启动
cp infra/env.example infra/.env
# 编辑 infra/.env，填入本机真实值

# 2. 启动本地基础设施
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d

# 3. 查看状态
docker compose -f infra/docker-compose.yml ps

# 4. 停止
docker compose -f infra/docker-compose.yml down
```

## 镜像名 / Tag 同步

任何修改 `infra/docker-compose.yml` 中镜像名 / Tag 的 PR，**必须同步更新 `deploy/helm/aegisops/values.yaml`** 与 `deploy/docker/*Dockerfile` 中的对应字段，否则本地与生产部署会漂移。

## 与 deploy/ 的边界

- `infra/` 不参与 CI 构建、不参与 Helm 渲染、不出现在生产镜像中
- `deploy/` 不参与 `docker compose up`、不承担本地开发依赖
- 任何键值对（密码 / token / secret）只在 `infra/env.example` 中以占位形式出现一次

## 密钥管理

详见 `infra/SECRETS.md`。
