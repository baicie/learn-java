---
title: MVP Roadmap 3 (Historical Draft)
type: phase
status: deprecated
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related:
  - docs/mvp-roadmap.md
  - .agents/skills/aegisops/SKILL.md
---

# MVP Roadmap 3 (Historical Draft)

# MVP Roadmap 3（历史草案，已 deprecated）

> **此文件已废弃。** 事实源入口：`docs/mvp-roadmap.md`（单一指 SKILL §14）。
> 历史内容仅供溯源查阅，禁止继续更新本文。2026-06-30 起按 PR7 落地替换。

---

# 我建议微调这份路线图

整体方向是对的，尤其这句我认为应该作为项目架构原则固定下来：

```txt
Java = 产品后端 / 权限 / 租户 / 数据 / 审计 / 审批
Python = Agent Runtime / LangGraph / 推理编排 / 工具调用
Runner = 自动化执行隔离层 / Ansible / SSH / Webhook
```

但结合我们已经从 Phase4.0 一路推进到 Phase5.3 的实际设计，我建议把 **Phase5.2 / Phase5.3 / Phase5.4** 重新对齐一下，否则路线会和当前实现发生偏移。

---

## 1. 当前路线和实际推进的差异

你贴的路线里是：

```txt
Phase5.2  aiops-runner 执行器
Phase5.3  Ansible / SSH / Webhook Adapter
Phase5.4  执行日志、回滚建议、执行审计
```

但我们现在实际已经把 Phase5.2 / Phase5.3 细化成了：

```txt
Phase5.2  execution_run / execution_step / runner dry-run 最小闭环
Phase5.3  runner lease / heartbeat / timeout / retry / artifact
```

也就是说，我们把原本 Phase5.4 的一部分“执行可靠性和日志沉淀”提前拆到了 Phase5.3，因为如果没有 lease、heartbeat、timeout、retry、artifact，直接接 Ansible/SSH/Webhook 风险太高。

所以我建议新的 Phase5 路线改成：

```txt
Phase5.0  Runbook 推荐与 AutomationPlan 草案
Phase5.1  审批流与风险分级
Phase5.2  aiops-runner dry-run 执行闭环
Phase5.3  runner 可靠性：lease / heartbeat / timeout / retry / artifact
Phase5.4  Webhook Adapter
Phase5.5  Ansible Adapter
Phase5.6  SSH Adapter
Phase5.7  回滚建议与回滚计划
Phase5.8  执行审计增强与执行报告
```

这样会比原来的 Phase5.3 一口气接三种真实执行适配器更稳。

---

# 2. 我建议最终采用的完整路线图

## Phase4：AI 诊断助手阶段

```txt
Phase4.0  Python LangGraph Diagnosis Agent
Phase4.1  Agent Context Contract & Safety Boundary
Phase4.2  可选真实 LLM Provider
Phase4.3  指标 / 日志 / 变更查询工具接入
Phase4.4  Agent 运行追踪与评测集
Phase4.5  jOOQ no-codegen 持久层治理
Phase4.6  jOOQ Codegen 正式化
Phase4.7  AI / RCA Repository 迁移到 generated Tables
Phase4.8  持久层命名治理：删除 AegisTables / Jdbc*Repository
```

Phase4 的最终目标不是“有个 AI 按钮”，而是：

```txt
Incident
  -> Alert
  -> RCA
  -> Evidence
  -> Agent Diagnosis
  -> Agent Trace
  -> Eval
```

也就是让 AI 诊断有证据、有链路、有评测、有回归。

---

## Phase5：Runbook / 审批 / 执行阶段

建议更新为：

```txt
Phase5.0  Runbook 推荐与 AutomationPlan 草案
Phase5.1  审批流与风险分级
Phase5.2  aiops-runner dry-run 执行闭环
Phase5.3  runner lease / heartbeat / timeout / retry / artifact
Phase5.4  Webhook Adapter
Phase5.5  Ansible Adapter
Phase5.6  SSH Adapter
Phase5.7  回滚建议与回滚计划
Phase5.8  执行审计增强与执行报告
```

### 为什么这样拆

原路线里：

```txt
Phase5.3 Ansible / SSH / Webhook Adapter
```

这个太大了，而且三个 adapter 的安全边界完全不同。

更合理的是：

```txt
Webhook 先做
Ansible 第二
SSH 最后
```

原因：

```txt
Webhook：安全边界最好，适合接内部低风险平台
Ansible：适合批量变更，但需要 inventory / playbook / dry-run / vault
SSH：风险最高，必须白名单、命令策略、凭证隔离、强审计
```

---

# 3. 修正后的 Phase5 详细顺序

## Phase5.0：Runbook 推荐与 AutomationPlan 草案

当前已设计：

```txt
runbook
runbook_step_template
automation_plan
automation_plan_step
```

核心原则：

```txt
AI 只推荐
Runbook 只生成计划
不执行
不审批
不调用 runner
```

状态：

```txt
draft
```

---

## Phase5.1：审批流与风险分级

当前已设计：

```txt
approval_policy
automation_approval
approval_decision
```

核心原则：

```txt
只有 approved plan 才能进入 execution
submitter 不能自批
reviewer 不能重复审批
high / critical 必须填写 comment
critical 至少 2 人审批
```

---

## Phase5.2：runner dry-run 执行闭环

当前已设计：

```txt
execution_run
execution_step
```

能力：

```txt
approved plan -> queued execution_run
runner claim queued run
manual step
shell dry-run step
unsupported step fail
run succeeded / failed
plan succeeded / failed
```

安全边界：

```txt
不 ProcessBuilder
不 Runtime.exec
不 SSH
不 Ansible
不 Webhook
```

---

## Phase5.3：runner 可靠性

当前已设计：

```txt
lease_until
heartbeat_at
attempt
max_attempts
retry_of_execution_id
execution_artifact
```

能力：

```txt
runner lease
runner heartbeat
timeout sweep
failed / timeout retry
artifact 记录
```

这一步非常关键，它让后续真实执行有基础保障。

---

## Phase5.4：Webhook Adapter

建议下一步真实执行先做 Webhook。

新增表：

```sql
webhook_connector
webhook_execution_policy
webhook_execution_log
```

能力：

```txt
配置 Webhook endpoint
配置 method / headers / body template
支持 dry-run preview
支持 timeout
支持 response capture
支持 allowlist
支持 sensitive header mask
```

安全策略：

```txt
只允许访问 allowlist host
禁止内网网段默认开放
禁止 file://
禁止 localhost
禁止 metadata IP
禁止重定向到非 allowlist
```

验收：

```txt
approved plan 可以触发 webhook dry-run
live webhook 默认关闭
开启 live 后只能访问 allowlist
所有请求响应摘要入 execution_artifact
```

---

## Phase5.5：Ansible Adapter

新增：

```sql
ansible_inventory
ansible_playbook
ansible_execution_policy
```

能力：

```txt
ansible --check dry-run
inventory 管理
playbook 参数模板
vault secret 引用
stdout/stderr artifact
```

安全策略：

```txt
只允许注册过的 playbook
只允许指定 inventory
变量必须 schema 校验
默认 --check
live 需要 high risk approval
```

---

## Phase5.6：SSH Adapter

SSH 最后做。

新增：

```sql
ssh_credential_ref
ssh_command_policy
ssh_execution_log
```

安全策略必须非常强：

```txt
默认关闭
只允许命令模板
禁止自由输入命令
禁止 rm -rf / mkfs dd shutdown reboot 等危险命令
禁止 sudo 默认开放
凭证只给 runner，不给 server
命令输出脱敏
全量审计
```

我建议 SSH Adapter 第一版只做：

```txt
只读命令
systemctl status
journalctl tail
df
free
top snapshot
```

不要第一版就做变更命令。

---

## Phase5.7：回滚建议与回滚计划

不要做“自动回滚”，先做：

```txt
AI 生成 rollback suggestion
Java 保存 rollback_plan
用户审批 rollback_plan
runner 执行 rollback_plan
```

新增：

```sql
rollback_plan
rollback_plan_step
rollback_approval
```

---

## Phase5.8：执行审计增强与执行报告

能力：

```txt
execution summary
execution diff
执行前后验证
artifact 汇总
operator timeline
导出 markdown
```

这一步可以衔接 Phase6 复盘。

---

# 4. Phase6 建议保持你的方向，但稍微调整顺序

你的 Phase6 是：

```txt
Phase6.0  复盘报告与知识沉淀
Phase6.1  Runbook 知识库与向量检索
Phase6.2  案例库 / 评测集 / Prompt 回归测试
```

我建议改成：

```txt
Phase6.0  Postmortem 复盘报告
Phase6.1  Case Library 事故案例库
Phase6.2  Knowledge Base / Vector Retrieval
Phase6.3  Agent Eval / Prompt Regression
```

原因：

```txt
先有结构化复盘
再沉淀案例库
再切 chunk 做向量检索
最后把案例库转成 eval 数据
```

---

# 5. Phase7 多 Agent 不要太早

你列的 Phase7 很对，但建议在这些条件满足后再做：

```txt
1. Agent 单体诊断已经稳定。
2. Evidence tool 已经稳定。
3. Runbook 推荐已经能用。
4. Eval 集至少有 30~50 个 case。
5. 安全边界已经经过多次回归。
```

否则多 Agent 会变成：

```txt
多个不稳定 Agent 互相放大不确定性
```

更稳的 Phase7：

```txt
Phase7.0  Graph 子图拆分，不急着多 Agent
Phase7.1  Human-in-the-loop checkpoint
Phase7.2  多 Agent role specialization
Phase7.3  Agent Memory
```

---

# 6. 我建议现在项目的真实下一步

你现在已经推进到：

```txt
Phase5.3 设计完成，待实现 / 审查
```

所以近期顺序应该是：

```txt
1. 完成 Phase5.3 实现
2. 审查 Phase5.3 commit
3. 修复状态一致性问题
4. 进入 Phase5.4 Webhook Adapter
```

不要马上做 Ansible / SSH。

---

# 7. 最终推荐路线

我建议最终定版成下面这个：

```txt
Phase4.0  Python LangGraph Diagnosis Agent
Phase4.1  Agent Contract & Safety Boundary
Phase4.2  OpenAI-compatible LLM Provider
Phase4.3  Evidence Tools：metrics / logs / changes
Phase4.4  Agent Trace & Eval
Phase4.5  jOOQ Persistence no-codegen
Phase4.6  jOOQ Codegen
Phase4.7  Repository generated table migration
Phase4.8  Persistence architecture cleanup

Phase5.0  Runbook Recommendation & AutomationPlan Draft
Phase5.1  Approval Workflow & Risk Policy
Phase5.2  Runner Dry-run Execution Loop
Phase5.3  Runner Reliability：lease / heartbeat / timeout / retry / artifact
Phase5.4  Webhook Adapter
Phase5.5  Ansible Adapter
Phase5.6  SSH Adapter
Phase5.7  Rollback Plan
Phase5.8  Execution Report & Audit

Phase6.0  Postmortem Report
Phase6.1  Incident Case Library
Phase6.2  Knowledge Base & Vector Retrieval
Phase6.3  Agent Eval & Prompt Regression

Phase7.0  Agent Graph Modularization
Phase7.1  Human-in-the-loop Checkpoint
Phase7.2  Multi-Agent Collaboration
Phase7.3  Agent Memory

Phase8.0  SaaS Multi-tenant Hardening
Phase8.1  Plugin System
Phase8.2  Private Deployment / Helm / Offline Package

Phase9.0  Production Observability & Operations
Phase9.1  Enterprise Security Baseline
Phase9.2  Backup / Restore / Disaster Recovery
Phase9.3  License / Edition / Offline Activation
```

---

# 8. Phase9：生产化与商业交付

Phase8.2 解决“如何交付私有部署包”，Phase9 解决“交付后如何长期、安全、可恢复地运行”。

其中：

```txt
Phase9.0 / 9.1 / 9.2：生产可用的必需项
Phase9.3：商业化私有部署的条件必需项，不属于技术 MVP 前置条件
```

---

## Phase9.0：Production Observability & Operations

### 是否需要

需要。AegisOps 自己也是生产系统；如果平台发生故障却无法被监控、定位和升级，它就无法可靠地承载 Incident 闭环。

### 目标

```txt
让 aiops-server / aiops-worker / aiops-runner / aiops-agent 自身可观测、可告警、可诊断、可升级。
```

### 交付

```txt
统一应用 metrics / logs / traces 规范
服务健康、依赖健康和队列积压指标
Agent 调用耗时、失败率、fallback 率和 token 用量指标
Runner lease、heartbeat、timeout、retry 和执行成功率指标
关键业务 SLI / SLO 与告警规则
容量、水位和保留周期监控
运维 Dashboard 与平台自身 Incident Runbook
滚动升级、数据库迁移和版本兼容检查
诊断包导出与脱敏
```

### 验收

```txt
1. 任一核心应用不可用时能在约定时间内告警。
2. 可以通过 traceId 串联 server、worker、agent、runner 调用链。
3. 队列积压、Agent 降级、Runner 超时均有指标和告警。
4. Dashboard 能展示核心 SLO、依赖状态和容量水位。
5. 升级前可执行兼容性检查，升级失败有明确恢复步骤。
```

### 不做

```txt
不把 AegisOps 扩展成完整 Prometheus / 日志 / Trace 替代平台
不为了自监控拆分新的微服务
不在这一阶段实现跨地域双活
```

---

## Phase9.1：Enterprise Security Baseline

### 是否需要

需要。Phase8.0 解决 SaaS 租户隔离和内部调用基线；Phase9.1 面向企业身份、密钥、供应链与合规要求，不能由 Phase8.0 替代。

### 目标

```txt
建立可审计、可轮换、可集成企业身份系统的安全基线。
```

### 交付

```txt
OIDC / OAuth2 企业单点登录，LDAP 作为可选适配器
细粒度 RBAC 与高风险操作二次确认
服务账号、短期凭证和 Token 生命周期管理
数据源密钥、Runner 凭证和 License 密钥分域存储
密钥加密、轮换、吊销和使用审计
登录、审批、插件、自动化、导出操作的审计增强
审计日志导出与防篡改校验
依赖漏洞扫描、SBOM、镜像签名和制品校验
安全响应头、CSRF/CORS、会话和密码策略基线
安装后的安全基线检查报告
```

### 验收

```txt
1. 可接入至少一个标准 OIDC Provider。
2. 所有高风险操作都经过权限判断、审批和审计。
3. 密钥不以明文落库、写日志或进入诊断包。
4. Token 和服务账号可以轮换、吊销并追踪使用者。
5. 发布制品带 SBOM 和完整性校验信息。
6. 安全基线扫描不存在未处理的 critical 问题。
```

### 不做

```txt
不自研企业身份提供商
不承诺本阶段即取得等保、ISO 27001 等正式认证
不允许插件或 Agent 绕过 Policy Engine、审批和 Runner
```

---

## Phase9.2：Backup / Restore / Disaster Recovery

### 是否需要

需要。私有部署如果只有备份脚本、没有经过验证的恢复流程，仍然不具备生产可用性。

### 目标

```txt
以明确 RPO / RTO 保护 Incident、审计、知识、配置和执行产物，并通过恢复演练证明可恢复。
```

### 交付

```txt
数据分级与备份范围清单
PostgreSQL 全量备份、增量/WAL 与时间点恢复
ClickHouse 备份和恢复策略
MinIO 对象版本、备份和完整性校验
配置、密钥引用、Helm values 和离线包元数据备份
备份加密、保留、轮换、校验和审计
单租户数据导出边界与整库恢复边界
恢复前兼容性检查与 Flyway 版本校验
自动化恢复演练和恢复结果报告
单节点故障、存储损坏和站点级灾难恢复 Runbook
```

Redis 只保存缓存、锁和临时状态，不作为权威数据备份源。

### 验收

```txt
1. 定义并验证 PostgreSQL、ClickHouse、MinIO 的 RPO / RTO。
2. 可以在干净环境恢复租户、Incident、审计、知识和附件。
3. 备份损坏、版本不兼容和密钥缺失时会安全失败。
4. 恢复演练自动生成包含耗时、缺失项和校验结果的报告。
5. 至少完成一次从备份到可登录、可查询 Incident 的完整演练。
```

### 不做

```txt
不把“备份成功”视为“恢复成功”
不默认备份明文密钥
不在这一阶段实现跨地域多活或自动无损切换
```

---

## Phase9.3：License / Edition / Offline Activation

### 是否需要

条件需要。只有在开始销售企业版、限制商业功能或交付完全离线环境时才实施；社区版和内部 MVP 不应被 License 阻塞。

### 目标

```txt
提供可离线验证、可审计、不会破坏客户数据与安全能力的版本授权机制。
```

### 交付

```txt
Community / Enterprise 等 Edition 与 Feature Entitlement 模型
签名 License 文件和离线公钥验证
离线申请码、激活码和续期流程
客户/部署标识、有效期、容量和功能声明
时钟回拨检测、宽限期和过期状态机
License 查看、导入、替换、吊销记录与审计
Helm / 离线包中的 License 注入方式
无外网环境的完整激活与续期 Runbook
```

### 安全与产品边界

```txt
License 失效不得删除、加密或破坏客户数据
License 失效不得关闭登录、审计、备份、恢复和数据导出
不得依赖在线远程 kill switch
不得使用脆弱的单一硬件指纹导致正常扩容或灾备后失效
不得让 License 判断进入 Incident、RCA、审批和 Runner 的核心安全逻辑
商业功能降级必须可预测、可观测并提供宽限期
```

### 验收

```txt
1. 完全离线环境可以完成申请、签发、激活和续期。
2. 被篡改或签名无效的 License 会被拒绝并写入审计。
3. 过期后核心只读、审计、备份、恢复和导出能力仍可使用。
4. Edition 与功能权限有集中定义，不在业务代码中散落判断。
5. 节点替换和灾备恢复后可按受控流程重新激活。
```

---

# 9. 结论

你贴的路线方向是对的，但我建议把 Phase5 拆细：

```txt
不要 Phase5.3 一口气做 Webhook + Ansible + SSH
先做 runner 可靠性
再做 Webhook
再做 Ansible
最后做 SSH
```

这样 AegisOps 会更像一个真实可控的 AIOps 产品，而不是一个“AI 能执行命令”的危险 demo。
