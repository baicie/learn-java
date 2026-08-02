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
移动到带时间戳的备份目录，上一把 Grant 公钥和 `kid` 会保留在当前运行目录供在途任务验证。
连续执行第二次 `--force` 前，必须确认使用更早公钥签发的诊断与执行任务已经完成或过期。运行
状态默认位于 `deploy/runtime/`，已被 Git 忽略。

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

默认 Diagnosis Grant 只包含 `diagnosis:execute` 和 `diagnosis:resume`。启用 Agent 的 evidence、
case、plugin、memory 或 checkpoint 能力时，必须按实际启用项显式扩展
`AIOPS_DIAGNOSIS_GRANT_SCOPES`，不得直接授予未使用的全部工具 scope。

## 从旧 Compose 升级

直接运行默认安装命令即可触发一次升级探测：

```bash
bash deploy/install.sh
```

当安装器发现旧 `aegisops-postgres` 或 `aegisops-core` PostgreSQL 服务时，会先执行 `pg_dump`
到 `deploy/runtime/backups/`，再创建 `aegisops_admin`、`aegisops_app`、`aegisops_runner` 角色，
转移数据库对象所有权，并把旧命名卷写入 `AIOPS_POSTGRES_VOLUME_NAME`。只有备份和事务迁移均
成功后才会删除被替代的旧业务容器；命名卷和备份不会删除。

升级前必须保留旧 Compose 文件、镜像标签和外部备份。升级失败时不要删除旧卷；先查看安装器
错误并继续使用旧栈。新栈已经写入 Flyway migration 后，回滚必须基于数据库兼容性评估，必要
时从 `pre-mtls-*.dump` 恢复，不能只切回旧镜像。

## Compose

主要文件：

```text
deploy/docker-compose.core.yml
deploy/install.sh
deploy/init/001-create-roles.sql
deploy/init/002-grant-runner.sql
deploy/init/003-migrate-legacy-owner.sql
deploy/scripts/migrate-legacy-compose.sh
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
