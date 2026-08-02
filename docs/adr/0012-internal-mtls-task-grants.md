---
title: 内部 mTLS 与任务授权分离
type: adr
status: accepted
phase: phase-8
owner: ai
created: 2026-08-01
updated: 2026-08-02
related:
  - docs/adr/0003-aiops-agent-boundary.md
  - docs/adr/0009-service-to-service-authentication.md
  - docs/adr/0010-service-authentication-oauth2-only.md
  - docs/adr/0011-default-core-deployment.md
  - docs/designs/phase-8/2026-08-01-mtls-task-grants.md
  - docs/api/internal-service-authentication.md
  - docs/deployment/production-security-checklist.md
---

# ADR 0012: 内部 mTLS 与任务授权分离

## 状态

已接受（2026-08-02）。

本 ADR 在实现与验证通过后替代 ADR 0010 的“Java 与 Agent 只使用 OAuth2 Client
Credentials”决策，以及 ADR 0011 的“Server 与 Worker 独立部署”和“AI 依赖 IdP”决策。
ADR 0010 关于短期凭证、audience、最小权限和失败关闭的安全目标继续有效；ADR 0011 关于
Runner 进程隔离、可选基础设施和禁止过早微服务的决策继续有效。

## 背景

AegisOps 的业务核心仍是 Java 模块化单体。原 Server 与 Worker 分成两个 JVM 并没有独立扩缩
容、数据所有权或团队边界，反而增加了配置、健康检查和发布编排。Agent 是 Python 诊断运行时，
Runner 是高权限执行隔离进程；这两个边界分别由语言运行时和权限风险决定，仍有独立部署价值。
当前完整 AI 档还为固定工作负载引入 Keycloak、三个 OAuth2 Client、Token Endpoint、JWKS、
Token 缓存和启动顺序，生产 VM 已因这些配置缺失而无法发布。

服务身份与业务任务授权是两个不同问题。工作负载证书适合证明固定组件身份；短期任务 Grant
适合绑定租户、Incident、执行计划、参数快照和有效期。继续把两者都交给通用服务 JWT 会增加
基础设施依赖，却仍不能单独保护被篡改的数据库执行任务。

## 决策

1. 原 Server 与 Worker 合并为单一 `aegisops-app` Java 进程。`apps/aiops-server` 暂作为 Maven
   启动模块路径，生产镜像、容器和 Spring 应用身份统一为 `aegisops-app`；后台作业由
   `modules/aiops-worker-runtime` 装入同一 JVM。
2. App 与 Agent 之间使用双向 TLS 认证工作负载身份，不再获取或校验 OAuth2 服务 Token。
   Agent 只信任 control-plane CA 签发且 URI SAN 为
   `spiffe://aegisops.local/service/aegisops-app` 的证书；App 内部 8443 Connector 只信任 agent CA
   签发且 URI SAN 为 `spiffe://aegisops.local/service/aiops-agent` 的证书。
3. 用户 JWT 只进入 App 公共 API。Agent 和 Runner 不接收、缓存或透传用户 JWT。
4. Diagnosis Grant 改用 Ed25519 签名，最长有效期 300 秒，绑定 `tenantId + incidentId +
traceId + diagnosisId + scope + audience`。Agent 在开始诊断前验证 Grant，并在回调 Java 时
   原样传播；Java 再次验证 Grant 并恢复租户上下文。
5. Runner 继续通过 PostgreSQL 原子领取任务，不增加 Runner HTTP API。控制面创建执行快照时
   签发 Ed25519 Execution Grant，绑定执行 ID、计划/Runbook、目标、参数摘要、模式和最长执行
   时间；Runner 在执行任何步骤前验证签名、有效期、审批状态和快照摘要。
6. Runner 使用独立数据库账号。Compose 自动初始化最小权限账号；外部 PostgreSQL/Helm
   部署提供可审计的 DBA SQL 模板，不由应用运行时尝试创建角色。
7. Compose 安装脚本生成根 CA、control-plane/agent 两条角色签发链、组件证书与 Ed25519
   Grant 密钥。私钥只挂载到 App，公钥挂载到 App、Agent 和 Runner；生产不提供共享 API Key
   或无 TLS 兼容模式。
8. Kubernetes 可由 cert-manager、SPIFFE 或 Service Mesh 替换证书签发与轮换，但 Diagnosis
   Grant 和 Execution Grant 的应用层契约保持不变。

## 影响

### 正向影响

- 完整 AI 档不再依赖 Keycloak/JWKS/Token Endpoint，私有化安装所需外部组件减少。
- Java 控制面从两个 JVM 收敛为一个 JVM，后台作业仍保留独立模块、租约、幂等和 Outbox 边界。
- 服务身份与租户/任务授权分离，证书泄露不等于获得任意 Incident 或 Runbook 权限。
- 数据库被非授权写入一条伪造执行任务时，Runner 会因缺少有效 Execution Grant 拒绝执行。
- 默认诊断档只运行 App、Agent 与 PostgreSQL；`automation` profile 再增加 Runner。

### 负向影响

- 必须维护内部 CA、证书到期监控和轮换命令。
- Spring Boot 公共 API 与 Agent 内部回调需要分离 TLS 边界，不能在公共端口强制客户端证书。
- 首次切换不兼容现有 OAuth2-only 完整发布，需要协调升级 App、Agent 与 Runner。
- Runner 数据库最小权限需随执行表访问面变化同步维护并测试。

## 备选方案

- 继续使用 OAuth2 Client Credentials：安全上可行，但当前固定组件规模下部署成本过高。
- 共享静态 API Key：拒绝。长期共享凭据无法表达任务范围，也不具备最小权限和可靠轮换。
- 只使用 mTLS：拒绝。mTLS 只能证明工作负载身份，不能绑定租户、Incident 或执行参数。
- 立即引入 SPIFFE/Istio：拒绝。保留升级路径，但当前单机/小规模部署不值得引入控制面。
- 保持 Worker 独立 JVM：拒绝。当前没有独立扩缩容或数据所有权收益，部署成本高于隔离收益。
- 将 Agent 或 Runner 合并进 App：拒绝。语言运行时和高权限执行边界仍需要隔离。

## 约束

- Agent 不直连业务数据库，也不调用 Runner。
- Runner 不读取签发私钥，不接受前端或 Agent 的直接调用。
- 外部只暴露 App 的 8080；App 8443、Agent 9008 与 Runner 状态端口只存在于内部网络。
- Grant 验证失败必须在执行前失败关闭并生成脱敏审计事件。
- 不允许通过关闭证书校验、信任所有证书或回退 HTTP 处理部署问题。
