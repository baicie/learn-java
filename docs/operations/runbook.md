---
title: AegisOps Production Runbook
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-03
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/adr/0013-isolate-optional-zabbix-api-network.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/production-security-checklist.md
---

# AegisOps Production Runbook

## 1. 运行拓扑

```text
诊断模式:   aegisops-app + aiops-agent + PostgreSQL
自动化模式: 诊断模式 + aiops-runner
```

外部只访问 App 8080。App 8443、Agent 9008 和 Runner 状态端口仅在内部网络可达。默认一键安装：

```bash
bash deploy/install.sh
```

启用自动化：

```bash
bash deploy/install.sh --mode automation
```

安装器默认复用已有安全材料。证书/密钥轮换必须先验证数据库凭据、当前/前一把公钥窗口和回滚
材料，不能直接删除 `deploy/runtime`。`--force` 只保留一代 previous Grant 公钥；再次轮换前
必须确认更早公钥对应的任务均已完成或过期。

可选 Demo Zabbix 使用独立部署入口：

```bash
bash deploy/scripts/deploy-zabbix.sh
```

App 与 Zabbix Web 都加入脚本幂等创建并校验的 internal bridge 网络
`aegisops-zabbix-api`，因此数据源 endpoint 可使用
`http://aegisops-zabbix-web:8080/api_jsonrpc.php`，无需把 Zabbix API 暴露到公网。若已存在同名但
不是 internal bridge 的网络，部署会 fail closed，不会自动删除或替换。

Zabbix Web 与 Server 默认分别只绑定 `127.0.0.1:8083` 和 `127.0.0.1:10051`。远程管理应使用
SSH 隧道；确需修改监听地址时，在 `.env.zabbix` 中显式设置 `ZABBIX_BIND_ADDRESS`，端口号分别
使用 `ZABBIX_WEB_PORT` 与 `ZABBIX_SERVER_PORT`，并先完成云安全组、TLS、认证和最小权限评审。
Agent2 与 Server 共享网络命名空间，内置 `Zabbix server` 主机应通过 `127.0.0.1:10050` 取值；
Agent2 不映射宿主机端口。

## 2. 容器未就绪

```bash
docker compose --env-file deploy/runtime/.env \
  -f deploy/docker-compose.core.yml --profile ai ps
docker compose --env-file deploy/runtime/.env \
  -f deploy/docker-compose.core.yml --profile ai logs --tail=200 aegisops-app aiops-agent
```

依次检查：

- PostgreSQL 与 `aegisops_app` 凭据；Runner 另查 `aegisops_runner`。
- App/Agent 证书文件、私钥权限、角色 CA 与证书有效期。
- Grant 当前 `kid`、公钥文件和仅 App 可见的私钥。
- App 8443 与 Agent 9008 的内部 DNS/网络连通性。
- 镜像拉取、资源限制与只读文件系统的临时目录。

不要通过关闭证书校验、回退 HTTP、重新启用静态 Token 或 OAuth2 兼容代码恢复服务。

## 3. Agent 高错误率

重点核对：

```text
AIOPS_AGENT_TLS_CERTIFICATE_FILE
AIOPS_AGENT_TLS_PRIVATE_KEY_FILE
AIOPS_AGENT_TLS_CLIENT_CA_FILE
AIOPS_AGENT_DIAGNOSIS_GRANT_PUBLIC_KEY_FILE
AIOPS_AGENT_DIAGNOSIS_GRANT_KEY_ID
AIOPS_AGENT_WORKFLOW_API_BASE_URL
AIOPS_AGENT_EVIDENCE_BASE_URL
```

`401` 通常表示缺失/无效/过期 Grant；`403` 通常表示 scope 或资源上下文不匹配；TLS 握手失败
应检查证书链、DNS SAN/URI SAN、时间同步和角色 CA，不会表现为业务 HTTP 状态码。

默认 Grant 只包含 `diagnosis:execute` 和 `diagnosis:resume`。若启用了 evidence、case、plugin、
memory 或 checkpoint 功能，检查 `AIOPS_DIAGNOSIS_GRANT_SCOPES` 是否只增加了对应 scope，并与
Agent 功能开关一致。Resume 请求和 checkpoint 状态中的 tenant、Incident、diagnosis、trace
必须全部一致。

## 4. Internal Agent 401/403

依次检查：

1. 请求是否进入 App 8443，而不是公共 8080。
2. Agent 证书是否由 agent CA 签发，URI SAN 是否为
   `spiffe://aegisops.local/service/aiops-agent`。
3. `X-AegisOps-Diagnosis-Grant` 是否由当前/前一把 Ed25519 公钥验证通过。
4. Grant 是否包含双 audience、端点所需 scope，以及匹配的 tenant/incident/diagnosis/trace。
5. 主机时间偏差是否超过允许窗口。

内部租户授权只来自有效 Diagnosis Grant。`X-Tenant-Id` 可以用于可观测，但不能作为替代凭据。

## 5. Runner 拒绝任务

查询执行记录和 `execution_audit_event`，关注 `execution_grant_rejected` 等脱敏事件。检查：

- Grant 是否缺失、过期、`kid` 未加载或 issuer/audience/scope 错误。
- `execution_snapshot_sha256` 是否与当前执行行及按 sequence 排序的步骤一致。
- 审批 ID/快照、mode、执行类型、回滚引用、重试次数或 timeout 是否被修改。
- live 审批快照是否为 `approved`，`approvalId`/`planId` 是否一致，批准数是否达到至少一次审批
  和 `requiredApprovals` 门槛。
- Runner 是否使用独立账号，且权限覆盖领取、心跳、状态、产物和审计所需表。

Grant 拒绝时不得手工改状态绕过验证，也不得把私钥挂入 Runner。应停止领取新任务，修复 App
签发或数据库一致性后重新走创建/审批流程。

## 6. 非破坏性 mTLS 检查

部署探针 `deploy/scripts/verify-internal-mtls.py` 使用客户端证书建立真实 TLS 连接，校验服务端
证书链、DNS 与预期 SPIFFE URI SAN。探针不得打印私钥、Grant、数据库密码或完整证书内容。

完整 Java → Agent → Java 业务烟测必须使用已批准的合成租户和 Incident，并确认不会触发
Runner、自动化审批或外部通知。

## 7. 发布与回滚

生产 workflow 只允许发布 `refs/heads/mvp`，且目标 commit SHA 必须存在成功的 `CI` run。
`Release Verify` 与手工 `Deploy Component` 共用 `aegisops-prod-deploy` 并发锁。部署材料先上传到
run 级 `deploy/staging/` 目录，在远端校验 SHA-256 manifest、Shell/Compose 和共享网络后，才由
`promote-deployment-candidate.sh` 备份并提升 active 文件。提升备份位于
`deploy/backups/promotion-<staging-id>/`；manifest、网络或候选文件校验失败时不得覆盖 active。

每次远端操作前必须先以 `--recover-only` 处理
`deploy/backups/.promotion-in-progress` journal。journal 指向的 promotion 备份同时保存旧 active
校验清单、缺失文件清单、候选副本和候选 manifest；完成标记或 active 校验不成立时恢复完整旧
状态。生产调用设置 `PROMOTION_REQUIRE_FLOCK=1`，目标机缺少 `flock` 时发布失败关闭。不要手工
删除 journal、临时文件或 promotion 备份来继续发布。

App 新镜像先通过 release preflight、Compose smoke、PostgreSQL migration、App/Agent 健康与
双向 mTLS 检查，之后推送并验证 registry 返回的 digest。生产 VM 只接收由 smoke job 输出的三个
`repository@sha256:<digest>`；commit SHA 标签仅用于定位发布，不是不可变部署凭据。部署后的合成
诊断不得触发 Runner、自动化审批或外部通知。

携带生产 SSH 凭据的 `appleboy/scp-action` 与 `appleboy/ssh-action` 必须固定完整 commit SHA，
并在每次连接前由 Runner 使用 OpenSSH `ssh-keyscan -t ed25519` 校验已离线核对的 ED25519
主机指纹。Appleboy 的 Go SSH 客户端默认优先协商 ECDSA，因此 action 自身同时固定当前协商的
ECDSA 指纹；两层校验都必须通过。轮换服务器 host key 时先从独立可信通道核对新指纹，再通过
PR 同步两个 workflow；不得临时关闭指纹检查。

当前生产信任材料：

```text
appleboy/scp-action  ff85246acaad7bdce478db94a363cd2bf7c90345
appleboy/ssh-action  823bd89e131d8d508129f9443cad5855e9ba96f0
ED25519 fingerprint  SHA256:t42JX0HGVD6m/KDVHYjoudZQGMv+8B4hkrfGJdZ8axY
Go SSH ECDSA fingerprint  SHA256:TtfGZDilKBdm05HX3b1i4yqG/mG0Ooas43ZBrFKyj2w
```

Action SHA 只能依据上游正式 release 和源码审查更新。服务器指纹同时从部署人员本机已知主机
记录与服务器公钥计算结果核对；仅从当前网络连接读取到的指纹不能单独作为信任依据。

回滚必须使用同一次已验证发布的镜像、Compose/Helm 描述符、证书信任窗口和 Grant 公钥集合。
如果新版本已经写入不可逆 migration，先按 migration 兼容性评估，不得只回滚镜像。Runner 已
领取的任务保留审计记录，不得删除数据库行来伪造回滚成功。

### 7.1 Core 备份与恢复

发布 workflow 必须在 promotion、旧版迁移、镜像 pull 和容器 up 之前创建并校验正式 Core 备份。
手工入口：

```bash
bash deploy/scripts/backup-core.sh
bash deploy/scripts/backup-core.sh \
  --validate deploy/backups/core-<UTC>-<pid>
```

备份包含数据库 custom-format dump、部署档位、旧 Compose、由实际运行镜像生成的精确 override、
镜像 ID 和 `SHA256SUMS`。普通回滚包不包含 `deploy/runtime/.env`、数据库密码、mTLS 私钥或 Grant
私钥；Secret 必须独立加密备份。恢复前确认当前 runtime Secret、卷名、目标 dump 和旧描述符兼容。

Core 恢复先校验网络与备份并创建 rescue backup，再使用临时旧 Compose 预拉全部回滚镜像。
预拉成功后才原子替换 active Compose、停止 Core 栈、启动旧 PostgreSQL，并在确认实际 image ID
后以单事务恢复 dump。脚本会在停栈前校验
`deploy/init/003-migrate-legacy-owner.sql`，并在恢复 dump 后以同一事务执行该脚本，把
`public`、`work_record` 等应用对象的 owner 恢复为 `aegisops_app`；文件缺失或 ownership
迁移失败，或 `aegisops_app` 角色不存在时会 fail closed，不会启动不完整的 Core：

```bash
bash deploy/scripts/restore-core.sh \
  --backup-dir deploy/backups/core-<UTC>-<pid> \
  --execute --confirm RESTORE-CORE
```

恢复完成后验证 PostgreSQL、App、Agent，按原部署档位验证 Runner，执行 App/Agent mTLS 探针并
检查 Incident 关键路径。不得执行 `down -v` 或删除命名卷。失败时停止后续发布，使用输出的 rescue
backup 恢复操作前状态。

### 7.2 Zabbix 发布与恢复

Zabbix 镜像必须在 Compose 中直接固定版本与 digest，不允许 `.env.zabbix` 覆盖镜像引用。
`deploy-zabbix.sh` 与 workflow 在 candidate 提升、pull/up 前调用同一状态探测：只有 PostgreSQL
容器和对应数据卷均不存在时才视为首次安装；容器停止或只剩数据卷时失败关闭，必须先以旧
Compose 和旧镜像恢复容器运行并创建备份。已有 PostgreSQL 运行时先调用 `backup-zabbix.sh`，
生成并校验 custom-format dump，再执行 pull/up；备份目录保存部署前 Compose、实际旧镜像生成的
`docker-compose.rollback.yml`、镜像信息和 `SHA256SUMS`，部署成功后追加部署后镜像信息。
workflow 必须在 candidate 提升 active 前生成该备份，并把路径显式传给部署脚本，不能在提升新
描述符后重新取代旧备份。

普通回滚包不得包含 `.env.zabbix` 或数据库密码；Secret 必须另行加密备份且不得进入 Git。
`deploy/backups/` 与 `deploy/staging/` 已整体 Git 忽略，但其中的 dump 仍按敏感数据保护。恢复使用
当前持久化 `.env.zabbix`，部署过程不轮换 Zabbix 数据库密码；恢复前必须验证当前 Secret 与目标
数据库一致。

恢复前先确认目标目录属于 `deploy/backups/`、校验和通过、目标镜像仍可用，并安排维护窗口。
恢复先校验共享网络属性和成员，再创建当前状态的 rescue backup。随后用临时旧 Compose 叠加
精确镜像 override 预拉四个回滚镜像；只有预拉成功才替换 active 描述符并停栈。启动 PostgreSQL
并确认实际 image ID 与备份一致后，才重建 `zabbix` 数据库并以
`--single-transaction --exit-on-error` 恢复 dump：

```bash
bash deploy/scripts/restore-zabbix.sh \
  --backup-dir deploy/backups/zabbix-<UTC>-<pid> \
  --execute --confirm RESTORE-ZABBIX
```

恢复完成后必须重新验证四个 Zabbix 容器、`127.0.0.1:10050` 的 `agent.ping=1`、open problems、
App 到 `aegisops-zabbix-web` 的 DNS/API，以及 Nebula 容器健康。不要执行 `down -v`、删除备份或
清理命名卷；Zabbix 恢复不应操作 AegisOps Core 与 Nebula 的容器、网络或卷。恢复失败时禁止
继续部署，按命令输出的 rescue backup 路径恢复恢复前状态。

## 8. 旧 Compose 升级

运行 `bash deploy/install.sh` 时，安装器会在启动新栈前探测旧 PostgreSQL 服务。发现旧栈后应
在输出中确认：

1. `deploy/runtime/backups/pre-mtls-*.dump` 已生成且非空。
2. `deploy/runtime/.env` 的 `AIOPS_POSTGRES_VOLUME_NAME` 指向旧命名卷。
3. 新 PostgreSQL 使用 `aegisops_admin`，App 与 Runner 分别使用最小权限账号。
4. App 健康、Flyway 完成、Agent mTLS 探针通过后，才把新栈视为可用。

备份或角色迁移失败时，旧容器与命名卷不得删除。自动迁移成功后旧业务容器会被移除，但 dump
和原卷保留；回滚需要旧 Compose/镜像以及数据库兼容性评估，必要时从 dump 恢复。不要为了
重试而执行 `docker volume rm`、`docker compose down -v` 或手工删除备份。
