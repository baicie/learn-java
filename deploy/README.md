# AegisOps 部署

生产运行时是三个业务进程，不是微服务拆分：

```text
aegisops-app   Java 模块化单体，Portal/API + Worker runtime
aiops-agent    Python AI 工作流，只通过 mTLS 调用 App
aiops-runner   可选高权限执行边界，只通过受限账号访问 PostgreSQL
```

PostgreSQL 是唯一必需的数据基础设施。Redis、MinIO、VictoriaMetrics 和内置 Zabbix 均为可选
能力；内部组件鉴权不依赖 Keycloak、OAuth2 Client Credentials 或 JWKS。

## 一键安装

默认诊断模式启动 App + Agent + PostgreSQL：

```bash
bash deploy/install.sh
```

启用审批后的自动化执行：

```bash
bash deploy/install.sh --mode automation
```

只生成证书、密钥、数据库密码和 `.env`，不启动容器：

```bash
bash deploy/install.sh --no-start
```

安装器可重复执行并复用现有材料。`--force` 会轮换 mTLS/Grant 材料并保留数据库密码；旧材料
移动到带时间戳的备份目录。运行状态默认位于 `deploy/runtime/`，已被 Git 忽略。

## 部署档位

| 模式 | 进程 | 用途 |
| --- | --- | --- |
| `core` | App + PostgreSQL | 无 AI 的控制面 |
| `diagnostic` | App + Agent + PostgreSQL | 默认 AI 诊断 |
| `automation` | App + Agent + Runner + PostgreSQL | AI 诊断与受控执行 |

外部只发布 App 8080。App 8443、Agent 9008 和 Runner 8092 不映射到宿主机或公网。

## 信任边界

```text
Browser -- user JWT --> App :8080
App -- mTLS + Diagnosis Grant --> Agent :9008
Agent -- mTLS + same Grant --> App :8443
App -- execution row + Execution Grant --> PostgreSQL <-- restricted Runner
```

App 是唯一任务授权签发者，持有 Ed25519 私钥。Agent 和 Runner 只持有公钥；Runner 还使用
`aegisops_runner` 最小权限数据库账号。Grant 或审批快照校验失败时必须在执行器调用前失败关闭。

## Compose

主要文件：

```text
deploy/docker-compose.core.yml
deploy/install.sh
deploy/init/001-create-roles.sql
deploy/init/002-grant-runner.sql
deploy/scripts/verify-internal-mtls.py
```

手工操作现有 runtime：

```bash
docker compose --env-file deploy/runtime/.env \
  -f deploy/docker-compose.core.yml --profile ai up -d --wait

docker compose --env-file deploy/runtime/.env \
  -f deploy/docker-compose.core.yml --profile ai --profile automation up -d --wait
```

不要手工创建共享数据库账号，也不要把 `aegisops_runner` 提升为 owner 或 superuser。

## Helm

Chart 位于 `deploy/helm/aegisops/`。默认启用 App + Agent，Runner 默认关闭。生产应优先引用
预创建的三个组件 Secret；也可用生成的私有 values：

```bash
bash scripts/deploy/generate-secrets.sh deploy/generated-secrets.values.yaml
helm lint deploy/helm/aegisops \
  -f deploy/generated-secrets.values.yaml \
  --set external.postgres.host=postgres.example.internal
```

启用 `networkPolicy.enabled=true` 时，必须为 PostgreSQL、模型提供方和执行目标提供受限 CIDR；
Chart 会拒绝空列表和全网 CIDR。Kubernetes 的 Runner 数据库角色与表权限仍由 DBA 使用
`deploy/init/*.sql` 初始化。

## 镜像

```bash
VERSION=0.1.0 REGISTRY=aegisops bash scripts/deploy/build-images.sh
```

生成：

```text
aegisops/aegisops-app:0.1.0
aegisops/aiops-agent:0.1.0
aegisops/aiops-runner:0.1.0
```

离线交付见 `deploy/offline/README.md`。

## 验证

```bash
bash scripts/ci/deployment.sh
python3 -m pytest deploy/tests -q
bash scripts/ci/release-preflight.sh
```

发布流水线复用 `deploy/install.sh`，构建并验证三个不可变镜像，再从 Agent 容器执行 App 8443
mTLS 探针。任何镜像健康、证书、Grant 或数据库权限校验失败都不得提升为成功发布。

## 可选 Zabbix

腾讯云或单机演示可单独使用 `deploy/docker-compose.zabbix.yml` 与
`deploy/scripts/deploy-zabbix.sh`。该栈不是默认安装依赖，Zabbix Web 端口也不应直接对公网开放。
