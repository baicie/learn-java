---
title: AegisOps Private Deployment
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-01
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
---

# AegisOps Private Deployment

## 部署方式

推荐使用 Helm 部署：

```bash
helm upgrade --install aegisops deploy/helm/aegisops \
  -n aegisops --create-namespace \
  -f deploy/helm/aegisops/values-private.yaml \
  -f deploy/generated-secrets.values.yaml
```

## 外部依赖

生产必需依赖只有 PostgreSQL。以下中间件按实际能力启用：

- PostgreSQL
- Redis（多副本共享限流/缓存）
- ClickHouse（高吞吐事件明细）
- MinIO（附件与产物）
- VictoriaMetrics（指标证据）

内部 App/Agent 通信不需要企业 IdP 或 Keycloak。用户登录仍使用 App 的 JWT/RBAC；如需接入
企业用户 OIDC，应作为公共身份接入单独配置，不得复用为内部工作负载鉴权。

## Secret

生成 Secret values：

```bash
scripts/deploy/generate-secrets.sh
```

生成的文件不要提交到 Git。

Secret 必须分别包含 App/Agent 证书私钥、角色 CA trust bundle、Grant 当前/前一把公钥、仅 App
可见的 Grant 私钥，以及互不相同的 `aegisops_app` / `aegisops_runner` 数据库凭据。生成文件
必须通过安全通道传递，不得提交到 Git 或打进镜像。

Compose 执行 `deploy/install.sh --force` 时会保留上一把 Grant 公钥与 `kid`。再次轮换前必须
确认使用更早公钥签发的诊断和执行任务均已完成或过期；Helm 滚动轮换同样必须同时配置
`security.taskGrant.current*` 与 `security.taskGrant.previous*`，完成窗口后再移除旧公钥。

默认 Diagnosis Grant 只授权 `diagnosis:execute` 与 `diagnosis:resume`。启用 evidence、case、
plugin、memory 或 checkpoint 工具时，通过 `AIOPS_DIAGNOSIS_GRANT_SCOPES` 仅增加实际需要的
scope，并同步 Agent 功能开关；不要把全部内部工具权限作为所有诊断任务的固定默认值。

## 旧 Compose 数据升级

`deploy/install.sh` 启动容器前会探测旧 `aegisops-postgres` 和 `aegisops-core` PostgreSQL 服务。
发现旧栈时，安装器先在 `deploy/runtime/backups/` 创建 custom-format `pg_dump`，再以单事务创建
新角色、转移对象所有权，并通过 `AIOPS_POSTGRES_VOLUME_NAME` 复用原命名卷。SQL 或备份失败
时不得删除旧业务容器或命名卷。

执行升级前应额外保留异机数据库备份、旧 Compose 描述符和旧镜像标签。自动迁移成功会删除被
替代的旧业务容器，但保留原卷和 dump；若新版本 Flyway 已执行，回滚前必须评估 schema 向后
兼容性，必要时在隔离环境验证 `pre-mtls-*.dump` 恢复后再切换生产。

## 网络隔离

生产 values 应启用 `networkPolicy.enabled=true`，并显式把
`networkPolicy.egress.allowedCidrs` 配置为实际外部数据服务网段；空列表、
`0.0.0.0/0` 与 `::/0` 会在 Helm 渲染阶段失败。`networkPolicy.egress.externalPorts`
按 app、runner、agent 分别维护端口白名单；未部署或未使用的外部依赖端口必须删除。Agent
默认只允许访问 App 8443 和配置的 LLM endpoint；App 与 Agent 的集群内双向调用由独立 Pod
selector 规则放行。Runner 只访问 PostgreSQL 和执行目标，不允许访问 Agent。

NetworkPolicy 不支持按域名匹配。使用外部域名时，仍需维护对应出口 CIDR；仅配置
`dnsNamespaceSelector` 只代表允许 DNS 查询，不代表允许访问任意解析结果。

## 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
kubectl logs -n aegisops deploy/aegisops-aegisops-app
```
