下面是我建议的 **AegisOps 从 Phase4 开始往后的完整路线图**。整体方向我建议固定为：

```txt
Java = 产品后端 / 权限 / 租户 / 数据 / 审计 / 审批
Python = Agent Runtime / LangGraph / 推理编排 / 工具调用
Runner = 自动化执行隔离层 / Ansible / SSH / Webhook
```

---

# 总体路线

```txt
Phase4.0  Python LangGraph Diagnosis Agent
Phase4.1  Agent Context Contract & Safety Boundary
Phase4.2  可选真实 LLM Provider
Phase4.3  指标 / 日志 / 变更查询工具接入
Phase4.4  Agent 运行追踪与评测集
Phase4.5  jOOQ 持久层治理
Phase5.0  Runbook 推荐与 AutomationPlan
Phase5.1  审批流与风险分级
Phase5.2  aiops-runner 执行器
Phase5.3  Ansible / SSH / Webhook Adapter
Phase5.4  执行日志、回滚建议、执行审计
Phase6.0  复盘报告与知识沉淀
Phase6.1  Runbook 知识库与向量检索
Phase6.2  案例库 / 评测集 / Prompt 回归测试
Phase7.0  多 Agent 协作
Phase7.1  持久化 Agent 状态与 Human-in-the-loop
Phase8.0  SaaS 化 / 多租户增强 / 插件市场
```

---

# Phase4：AI 诊断助手阶段

## Phase4.0：Python LangGraph Diagnosis Agent

目标：先把 Agent Runtime 跑通，不追求真实 LLM 效果。

```txt
FastAPI + LangGraph OSS
单进程 Agent
deterministic/mock 诊断
Java 调 Python
ai_diagnosis 落库
incident_timeline 写入 ai_diagnosed
前端展示 AI Diagnosis
```

交付物：

```txt
apps/aiops-agent
modules/aiops-ai-client
V5__phase4_ai_diagnosis.sql
AI Diagnose 按钮
Python pytest
Java 单测
```

验收：

```txt
用户点 AI Diagnose
Java 查询 Incident / Alerts / RCA
Java 调 Python /v1/diagnose
Python 返回结构化诊断
Java 写 ai_diagnosis
前端展示诊断结果
```

---

## Phase4.1：Agent Context Contract & Safety Boundary

目标：把 Java 和 Python 之间的协议正式化，防止后面 Agent 工具越来越多后失控。

新增内容：

```txt
AgentDiagnosisRequest schema 固定
AgentDiagnosisResponse schema 固定
traceId / tenantId / incidentId 全链路贯穿
internal token 校验
Agent 不直接访问 DB
Agent 不直接执行动作
Agent 不直接连接生产系统
```

建议增加：

```txt
apps/aiops-agent/contracts/diagnosis.schema.json
modules/aiops-ai-client/src/test/resources/agent-diagnosis-response.json
```

验收：

```txt
Java 侧 contract test 通过
Python 侧 schema test 通过
字段新增必须兼容老版本
Agent 输出缺字段时 Java 能 sanitize
```

---

## Phase4.2：可选真实 LLM Provider

目标：在 deterministic 的基础上接真实模型，但必须可降级。

```txt
generation_mode=deterministic
generation_mode=openai-compatible
LLM prompt builder
严格 JSON parser
invalid JSON fallback
provider error fallback
raw response 入库
```

推荐先支持：

```txt
OpenAI-compatible /chat/completions
DeepSeek
Qwen
OpenAI
本地模型服务
```

不直接绑定某一家。

验收：

```txt
deterministic 模式 CI 可跑
openai-compatible 模式能调真实模型
模型返回非法 JSON 时自动 fallback
ai_diagnosis.response_raw 保存原始结果
```

---

## Phase4.3：指标 / 日志 / 变更查询工具接入

目标：Agent 不再只看 Incident/RCA/Alert，而是开始具备真实排障上下文。

### 4.3.1 指标工具

```txt
query_metrics
  输入：tenantId, assetId, metricName, start, end
  输出：series summary / anomaly / top points
```

数据源：

```txt
VictoriaMetrics
Prometheus-compatible API
```

先做：

```txt
CPU
Memory
Disk
Network
Service latency
Error rate
```

### 4.3.2 日志工具

```txt
query_logs
  输入：tenantId, assetId/service, keyword, start, end
  输出：top log patterns / error samples / count trend
```

数据源：

```txt
ClickHouse
```

### 4.3.3 变更工具

```txt
query_changes
  输入：assetId/service, start, end
  输出：deploy / config change / restart / manual operation
```

先可以用内部表：

```txt
change_event
deployment_event
operation_event
```

验收：

```txt
AI Diagnosis 结果能引用 metrics/logs/change evidence
raw.evidence 里有 tool outputs
不允许 Agent 自己编造指标
```

---

## Phase4.4：Agent 运行追踪与评测集

目标：Agent 能不能变好，要有评测和回归。

新增表：

```txt
agent_run
agent_tool_call
agent_eval_case
agent_eval_result
```

能力：

```txt
每次诊断记录 graph 节点耗时
记录工具输入输出摘要
记录 LLM token / provider / model
记录 fallback 原因
构建固定事故样本评测集
```

评测维度：

```txt
结构化输出合法性
是否越权建议
是否引用证据
根因候选是否合理
排障步骤是否可执行
是否出现幻觉
```

验收：

```txt
能回放一次 Agent run
能对 10 个固定样本跑 eval
Prompt 改动后能做回归
```

---

## Phase4.5：jOOQ 持久层治理

目标：解决 Java 里复杂 SQL 字符串越来越多的问题。

内容：

```txt
新增 aiops-persistence
引入 spring-boot-starter-jooq
Incident/RCA/AI 三块复杂 Repository 改为 jOOQ
保留 Repository interface 不变
Service/Controller 不变
```

第一阶段不做 codegen，先用集中式表字段常量。

后续 Phase4.6 再考虑：

```txt
jOOQ Codegen
Flyway schema source
generated Tables
```

验收：

```txt
Incident 聚合正常
RCA 正常
AI Diagnose 正常
复杂查询不再继续扩散 JdbcTemplate SQL
```

---

# Phase5：Runbook 与自动化执行阶段

Phase5 是从“诊断建议”走向“可控执行”的阶段。这里一定要谨慎。

---

## Phase5.0：Runbook 模型与推荐

目标：AI 不直接执行，只推荐 Runbook。

新增表：

```sql
runbook
runbook_step
runbook_version
runbook_binding
runbook_recommendation
```

Runbook 类型：

```txt
manual         人工步骤
query          查询型步骤
script         脚本型步骤
ansible        Ansible playbook
webhook        Webhook 调用
```

Runbook 风险等级：

```txt
low
medium
high
critical
```

Runbook 推荐依据：

```txt
incident.severity
alert fingerprint
asset type
rca root cause
历史案例
标签匹配
向量检索
```

验收：

```txt
Incident 详情页展示推荐 Runbook
每条推荐有 reason / confidence / risk
不能直接执行
```

---

## Phase5.1：AutomationPlan 与审批流

目标：把“建议执行”变成“执行计划”，先审批再执行。

新增表：

```sql
automation_plan
automation_plan_step
approval_request
approval_decision
```

状态流：

```txt
draft
pending_approval
approved
rejected
executing
succeeded
failed
cancelled
```

风险策略：

```txt
low      可一键执行，可配置是否免审批
medium   至少一人审批
high     管理员审批
critical 默认禁止自动执行
```

验收：

```txt
AI 可以 propose plan
Java 保存 plan
用户必须点 approve
未审批不能进入 runner
```

---

## Phase5.2：aiops-runner 执行器

目标：执行层和 server 解耦，避免产品后端直接 SSH/Ansible。

架构：

```txt
aiops-server
  ↓ create execution
aiops-runner
  ↓ poll / receive task
adapter
  - ansible
  - ssh
  - webhook
```

新增表：

```sql
automation_execution
automation_execution_step
automation_execution_log
```

Runner 原则：

```txt
最小权限
任务隔离
超时控制
输出脱敏
执行日志不可篡改
失败可重试
```

验收：

```txt
runner 可执行 dry-run 任务
执行日志回传 server
失败状态可见
```

---

## Phase5.3：Ansible / SSH / Webhook Adapter

目标：接入真实执行能力。

优先级：

```txt
P0 Webhook Adapter
P1 Ansible Adapter
P2 SSH Adapter
```

为什么 Webhook 先做：

```txt
安全边界更清晰
容易测试
适合接内部系统
不需要直接拿主机密钥
```

Adapter 接口：

```txt
validate(plan)
dryRun(plan)
execute(plan)
rollback(plan)
```

验收：

```txt
Webhook 可执行
Ansible 可 dry-run
SSH 默认关闭
高风险动作被拦截
```

---

## Phase5.4：执行审计与回滚建议

目标：自动化动作可追踪、可解释、可回滚。

能力：

```txt
执行前快照
执行日志
执行后验证
失败诊断
回滚建议
回滚 plan
```

注意：Phase5.4 仍不建议默认自动回滚，应该是：

```txt
AI 生成 rollback suggestion
用户审批后执行 rollback plan
```

---

# Phase6：复盘与知识沉淀

Phase6 是 AIOps 能不能形成壁垒的关键。

---

## Phase6.0：事故复盘报告

自动生成：

```txt
事故摘要
时间线
告警序列
RCA 证据
AI 诊断
执行动作
恢复时间
影响范围
改进建议
```

新增表：

```sql
postmortem
postmortem_section
postmortem_action_item
```

前端：

```txt
Incident Detail -> Postmortem Tab
一键生成复盘
人工编辑
导出 Markdown / PDF
```

---

## Phase6.1：知识库与向量检索

目标：让历史事故反哺未来诊断。

知识来源：

```txt
postmortem
runbook
incident resolution
manual notes
执行日志摘要
```

新增：

```txt
knowledge_document
knowledge_chunk
embedding_index
```

向量库：

```txt
Milvus
pgvector
Qdrant
```

你当前前面设想里有 Milvus，可以优先 Milvus。

能力：

```txt
相似事故检索
相似 Runbook 推荐
历史解决方案引用
```

---

## Phase6.2：Agent Eval 与 Prompt 回归

目标：让 Agent 迭代可控。

评测集：

```txt
真实事故样本
模拟事故样本
边界安全样本
错误 RCA 样本
误导性告警样本
```

指标：

```txt
JSON 合法率
证据引用率
越权建议率
根因命中率
步骤可执行性
幻觉率
```

CI 中可跑：

```txt
agent eval smoke
agent eval safety
agent eval regression
```

---

# Phase7：多 Agent 协作

Phase7 再引入多 Agent，不要太早。

---

## Phase7.0：多 Agent 拆分

```txt
Triage Agent       初步分诊
RCA Agent          根因分析
Metrics Agent      指标分析
Logs Agent         日志分析
Runbook Agent      Runbook 推荐
Safety Agent       风险审查
Postmortem Agent   复盘生成
```

LangGraph 适合把这些 Agent 作为节点或子图组织起来。

---

## Phase7.1：Human-in-the-loop

能力：

```txt
Agent 暂停等待人工输入
审批后继续
用户补充上下文后重跑部分节点
保留 checkpoint
```

这里可以考虑：

```txt
LangGraph checkpoint
Postgres checkpoint store
Redis queue
```

---

## Phase7.2：Agent Memory

分两类：

```txt
短期记忆：本次 incident 的上下文
长期记忆：历史事故、Runbook、偏好、组织规则
```

长期记忆必须可审计，不要黑盒。

---

# Phase8：平台化与商业化能力

---

## Phase8.0：多租户增强

```txt
租户隔离
数据源隔离
Agent 配额
模型配额
执行配额
审计日志
操作水印
```

---

## Phase8.1：插件系统

插件类型：

```txt
Datasource Plugin
Metric Plugin
Log Plugin
Runbook Plugin
Executor Plugin
Notification Plugin
```

例如：

```txt
Zabbix
Prometheus
VictoriaMetrics
ClickHouse
Loki
Jenkins
Kubernetes
Ansible
DingTalk
WeCom
Slack
```

---

## Phase8.2：私有化部署

```txt
Docker Compose
Helm Chart
离线镜像包
初始化脚本
配置向导
健康检查
备份恢复
```

---

# 我建议你真正采用的顺序

不要把 Phase4.5 提前压住 Phase4 主线。最合理顺序是：

```txt
Phase4.0  先完成 LangGraph deterministic Agent
Phase4.2  接入可选 LLM Provider
Phase4.3  接入 metrics/logs/change 工具
Phase4.4  加 Agent tracing/eval
Phase4.5  再做 jOOQ 持久层治理
Phase5.0  Runbook 推荐
Phase5.1  审批流
Phase5.2  Runner
```

也就是：

```txt
先让 Agent 真有诊断价值
再治理 Java 持久层
最后再碰自动化执行
```

---

# 近期最关键的 5 个里程碑

## M1：Phase4.0

```txt
用户能点 AI Diagnose
得到稳定 mock/deterministic 诊断
```

## M2：Phase4.2

```txt
能接真实模型
模型异常也不会影响产品可用性
```

## M3：Phase4.3

```txt
Agent 能查指标、日志、变更
诊断开始有真实证据
```

## M4：Phase5.0

```txt
Agent 能推荐 Runbook
但不能执行
```

## M5：Phase5.2

```txt
审批后 runner 可执行低风险动作
```

---

# 最终方向判断

我建议你的 AegisOps 不要做成单纯“AI 聊天运维助手”，而要做成：

```txt
事故中心
  +
证据链
  +
Agent 诊断图
  +
Runbook 推荐
  +
审批执行
  +
复盘沉淀
```

这条路线才更像真正的 AIOps 产品，而不是套壳 LLM。
