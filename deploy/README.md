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
deploy/scripts/ensure-zabbix-api-network.sh
deploy/scripts/migrate-legacy-compose.sh
deploy/scripts/verify-internal-mtls.py
```

手工操作现有 runtime：

```bash
bash deploy/scripts/ensure-zabbix-api-network.sh

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

发布流水线复用 `deploy/install.sh`，构建并验证三个镜像，再从 Agent 容器执行 App 8443 mTLS
探针。smoke 通过后才推送镜像，并校验仓库返回的 digest；生产部署只接收三个
`repository@sha256:<digest>` 引用，不使用可被覆盖的 commit SHA 标签。任何镜像健康、证书、
Grant、数据库权限或 registry digest 校验失败都不得进入生产部署。

## 生产发布与 Core 恢复

生产材料先上传到 run 级 `deploy/staging/`，校验 manifest 后再提升到 active 目录。
`promote-deployment-candidate.sh` 使用部署锁和持久化 journal；每次发布在读取 active 描述符前先
执行恢复检查：

```bash
PROMOTION_REQUIRE_FLOCK=1 bash deploy/scripts/promote-deployment-candidate.sh \
  --active-root "$HOME/workspace/aegisops" \
  --recover-only
```

若进程在逐文件提升期间被强制终止，下一次 `--recover-only` 会校验 promotion 备份中的旧文件
清单、候选副本和校验和，再恢复完整旧 active 状态。生产目标机必须提供 `flock`；不得手工删除
`deploy/backups/.promotion-in-progress` 或半成品备份来跳过恢复。

Core 独立备份与校验：

```bash
bash deploy/scripts/backup-core.sh
bash deploy/scripts/backup-core.sh \
  --validate deploy/backups/core-<UTC>-<pid>
```

备份位于 `deploy/backups/core-<UTC>-<pid>/`，包含数据库 custom-format dump、部署档位、旧
Compose、精确镜像 override、镜像 ID 和 `SHA256SUMS`。普通回滚包不复制
`deploy/runtime/.env`、数据库密码、mTLS 私钥或 Grant 私钥；这些 Secret 必须独立加密备份。

Core 恢复会先校验备份和共享网络、保存当前状态的 rescue backup，并用临时旧 Compose 预拉
全部回滚镜像。只有预拉成功后才原子替换 active Compose、停栈和重建数据库：

```bash
bash deploy/scripts/restore-core.sh \
  --backup-dir deploy/backups/core-<UTC>-<pid> \
  --execute --confirm RESTORE-CORE
```

恢复使用当前 `deploy/runtime/.env`，因此必须先确认它与目标 dump、卷名和证书材料兼容。只在
维护窗口执行；恢复后重新验证容器镜像 ID、App/Agent mTLS、数据库权限和关键业务路径。失败时
使用命令输出的 rescue backup 恢复操作前状态。

## 可选 Zabbix

腾讯云或单机演示可单独使用 `deploy/docker-compose.zabbix.yml` 与
`deploy/scripts/deploy-zabbix.sh`。该栈不是默认安装依赖。脚本创建并校验 internal bridge 网络
`aegisops-zabbix-api`，Compose 只将 App 与 Zabbix Web 接入该网络用于 API 通信；Web 与 Server
的宿主机端口默认绑定 `127.0.0.1`，不应直接对公网开放。

四个 Zabbix 镜像直接固定到明确版本与 digest，不接受环境变量覆盖。升级时必须修改 Compose、
通过 PR 与恢复演练后再部署。Agent2 与 Zabbix Server 共享网络命名空间，使内置
`Zabbix server` 主机通过 `127.0.0.1:10050` 检查 Agent，同时不发布 Agent 端口。

部署脚本会先区分首次安装与已有状态。只有 PostgreSQL 容器和对应 Compose 数据卷都不存在时，
才允许无备份安装；容器停止或只剩数据卷时会在 `pull/up` 前失败，必须先用旧描述符与旧镜像
恢复容器运行，再创建备份。已有 PostgreSQL 正在运行时，脚本会在拉取或启动新镜像前创建
custom-format dump，用 `pg_restore --list` 校验，并记录部署前后的镜像 ID/digest。备份中的
`docker-compose.rollback.yml` 由实际运行镜像生成，恢复时与旧 active 描述符叠加，避免浮动标签
或已提升的新描述符把旧数据库重新交给新镜像。备份默认位于
`deploy/backups/zabbix-<UTC>-<pid>/`；该目录与 `deploy/staging/` 已整体 Git 忽略，但数据库 dump
仍可能包含敏感业务数据，目录和文件必须保持仅部署账号可读。

普通回滚包不包含 `.env.zabbix` 或数据库密码。生产 Secret 必须通过独立、加密且不进入 Git 的
方式备份。恢复使用当前持久化 `.env.zabbix`；本部署流程不会轮换 Zabbix 数据库密码，执行恢复前
必须确认当前 Secret 与目标数据库一致。

独立创建备份：

```bash
bash deploy/scripts/backup-zabbix.sh
```

恢复会停止 Zabbix 栈、替换数据库并重新启动。它先保存恢复前的 rescue backup，且只有同时提供
执行开关和确认口令才会调用 Docker：

```bash
bash deploy/scripts/restore-zabbix.sh \
  --backup-dir deploy/backups/zabbix-<UTC>-<pid> \
  --execute --confirm RESTORE-ZABBIX
```

只在维护窗口内执行恢复，并先核对备份 `SHA256SUMS`、目标镜像和数据库兼容性。恢复入口只操作
`aegisops-zabbix` Compose 项目，并在 rescue backup、停栈或数据库替换前执行共享网络属性与成员
guard。脚本先用临时旧 Compose 预拉四个精确回滚镜像，成功后才替换 active 描述符并停栈；
PostgreSQL 启动后还会比对备份记录的 image ID，再执行数据库恢复。不得停止或删除 AegisOps
Core、Nebula 容器或其卷。`pg_restore` 使用单事务并在首个错误退出，避免留下部分恢复的 schema；
失败时使用输出的 rescue backup 回滚恢复前状态。
