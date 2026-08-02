---
title: 生产安全检查清单
type: operation
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-08-03
related:
  - docs/adr/0012-internal-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
---

# 生产安全检查清单

## 必须配置

- [ ] `aegisops-prod` Environment 已配置 required reviewers 与仅允许 `mvp` 的 deployment branch policy
- [ ] `mvp` 已启用 branch protection，required checks 与审批规则不能被普通写入者绕过
- [ ] 生产 VM、DockerHub 与模型凭据已迁移为 `aegisops-prod` Environment secrets
- [ ] 用户 JWT 签名密钥已替换默认值并通过 Secret 注入
- [ ] PostgreSQL `aegisops_app` 使用随机密码
- [ ] PostgreSQL `aegisops_runner` 使用不同随机密码和最小表权限
- [ ] control-plane CA 与 agent CA 的私钥离线保存，不进入运行容器
- [ ] App/Agent 证书 URI SAN 与内部鉴权契约一致
- [ ] Grant Ed25519 私钥只挂载到 `aegisops-app`
- [ ] Agent 与 Runner 只挂载当前/前一把 Grant 公钥
- [ ] 外部 Redis、ClickHouse、MinIO 启用时使用独立凭据

## 网络与容器

- [ ] 外部只暴露 `aegisops-app:8080`，并由 Ingress/反向代理终止公网 TLS
- [ ] App 8443、Agent 9008 与 Runner 状态端口未映射到宿主机或公网
- [ ] App 与 Agent 双向 TLS 均为 required，未使用 `client-auth=want`
- [ ] Agent 不接收数据库 Secret、Grant 私钥或 Runner 凭据
- [ ] Runner 不接收 App/Agent TLS 私钥或 Grant 私钥
- [ ] Runner 容器只读运行，临时目录大小受限
- [ ] Kubernetes 使用独立 ServiceAccount、Secret 与 NetworkPolicy
- [ ] 出站 CIDR 仅包含实际外部依赖，不使用 `0.0.0.0/0` 或 `::/0`
- [ ] 禁止使用 `latest` 镜像标签，并配置 resource requests / limits
- [ ] Core 生产镜像均为 smoke job 输出并经 registry 校验的 `repository@sha256:<digest>`
- [ ] 携带生产凭据的第三方 SSH/SCP Action 固定完整 commit SHA，且每次连接校验 ED25519 主机指纹

## AegisOps

- [ ] 内部 App/Agent 鉴权只使用 mTLS + Diagnosis Grant，不存在静态 Token/OAuth2 兼容路径
- [ ] Diagnosis Grant 使用 Ed25519，TTL 不超过 300 秒并校验 issuer/audience/scope/resource
- [ ] 内部租户上下文只从有效 Diagnosis Grant 恢复
- [ ] Execution Grant 覆盖审批快照和全部不可变执行步骤字段
- [ ] Runner 对缺失、过期、篡改 Grant 失败关闭且零执行器调用
- [ ] Execution Grant 拒绝写入脱敏审计事件
- [ ] public/internal API rate limit enabled
- [ ] plugin tool policy 默认关闭或显式 allow
- [ ] Runner live 执行默认关闭
- [ ] Ansible / SSH adapter 默认关闭

## 轮换与备份

- [ ] 证书到期监控已启用，轮换演练支持新旧 CA 短期并存
- [ ] Grant 公钥轮换演练支持当前/前一把 `kid`
- [ ] PostgreSQL backup 已验证恢复
- [ ] Core 回滚包不含 runtime `.env` 或 Secret，Core rescue backup 与完整恢复已演练
- [ ] promotion 强制使用 `flock`，强杀后 `--recover-only` journal 恢复已演练
- [ ] Zabbix 普通回滚包不含 `.env.zabbix`，数据库 Secret 已独立加密备份
- [ ] Zabbix 停机容器与孤立数据卷升级会在 candidate 提升和镜像拉取前失败关闭
- [ ] Zabbix 恢复会在替换 active 描述符、停栈和删库前预拉全部精确回滚镜像
- [ ] 可选 ClickHouse / MinIO 已配置备份
- [ ] Helm/Compose Secret 有加密备份且不进入 Git
