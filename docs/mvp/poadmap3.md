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
```

---

# 8. 结论

你贴的路线方向是对的，但我建议把 Phase5 拆细：

```txt
不要 Phase5.3 一口气做 Webhook + Ansible + SSH
先做 runner 可靠性
再做 Webhook
再做 Ansible
最后做 SSH
```

这样 AegisOps 会更像一个真实可控的 AIOps 产品，而不是一个“AI 能执行命令”的危险 demo。
