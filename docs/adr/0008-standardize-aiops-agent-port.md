---
title: 统一 aiops-agent 运行端口
type: adr
status: accepted
phase: global
owner: ai
created: 2026-07-29
updated: 2026-07-29
related:
  - docs/adr/0003-aiops-agent-boundary.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
  - infra/docker-compose.yml
  - deploy/helm/aegisops/values.yaml
---

# ADR 0008: 统一 aiops-agent 运行端口

## 状态

已接受（2026-07-29）。

## 背景

ADR 0003 已确定 aiops-agent 是通过 HTTP 与 Java 解耦的独立 Python 诊断运行时，但其“事实”
章节仍记录 Helm 端口为 8000。当前 Dockerfile、Docker Compose、Java Agent client 默认地址和
架构端口表均使用 9008；若 Helm 继续使用 8000，会使部署清单、NetworkPolicy、Service 与 Java
调用地址出现两套默认值，增加诊断调用失败和环境漂移风险。

ADR 0003 已处于 `accepted`，按文档治理规则不直接改写。本 ADR 只替代其中关于 Helm 端口
8000 的历史事实，不改变它定义的 HTTP + internal token、禁止直连数据库、契约版本校验和
禁止 Agent 直接触发 Runner 等边界。

## 决策

1. aiops-agent 的仓库默认运行端口统一为 9008。
2. Dockerfile、Docker Compose、Helm `apps.agent.port`、Service、NetworkPolicy、Java
   `AIOPS_AGENT_BASE_URL` 默认值和架构端口表必须保持一致。
3. 部署若覆盖端口，必须同时更新容器监听端口、Service/NetworkPolicy 和 Java Agent client
   地址，不能只修改其中一处。
4. Agent HTTP 接口与 contract version 不变，本决策不引入新的外部业务 API。

## 影响

正向影响：

- 本地、Compose、Helm 和 Java client 使用同一默认值，减少环境专属配置。
- Helm 测试可以对容器端口、Service、NetworkPolicy 与 Agent base URL 做一致性断言。

负向影响：

- 仍依赖 8000 的既有私有部署需要同步调整 Service、NetworkPolicy 与调用地址。
- 自定义端口不再能只改 Helm 单个字段，必须按完整调用链一起变更。

## 备选方案

- 保留 Helm 8000，通过 Service 转发到容器 9008：拒绝。它保留两套默认端口，继续增加排障成本。
- 全部改为 8000：拒绝。Docker、Agent 文档和 Java 默认配置已稳定使用 9008，迁移面更大且没有收益。

## 相关文档

- `docs/adr/0003-aiops-agent-boundary.md`
- `.agents/skills/aegisops/references/architecture-boundaries.md`
- `infra/docker-compose.yml`
- `deploy/helm/aegisops/values.yaml`
