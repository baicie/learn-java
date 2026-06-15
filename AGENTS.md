# AGENTS.md

本文件用于指导 AI 编码 Agent 在本仓库中进行设计、开发、重构、测试与评审。

项目暂定名：**AegisOps / FaultLens**

当前阶段目标：构建一个面向中小团队与私有化场景的 AI Ops MVP，实现从 **Zabbix 告警 → Incident 聚合 → RCA 证据链 → AI 诊断 → Runbook 推荐 → Ansible 执行 → 复盘沉淀** 的最小闭环。

---

## 1. 产品定位

本项目不是传统监控系统的简单替代品，也不是只有聊天框的 AI 工具。

本项目核心定位：

> 面向可观测数据、运维告警和自动化处置的 AI 事故诊断平台。

第一阶段重点不做大而全 AIOps，而是先做可落地的事故闭环。

MVP 主线：

```txt
Zabbix 告警进入
  ↓
转换为统一 AlertEvent
  ↓
聚合成 Incident
  ↓
查询相关指标、资产、历史事件
  ↓
RCA 规则生成证据链
  ↓
AI 生成诊断报告
  ↓
推荐 Runbook
  ↓
人工确认后执行 Ansible
  ↓
执行结果写回 Incident
  ↓
生成复盘报告
  ↓
沉淀为历史知识
```

---

## 2. 核心原则

所有 Agent 在修改代码前必须遵守以下原则：

### 2.1 围绕 Incident 设计

本项目核心领域模型是 `Incident`，不是单条告警、不是单个指标、也不是大模型对话。

任何功能都应回答：

```txt
这个功能是否能帮助发现、定位、处理或复盘 Incident？
```

如果不能，应推迟。

---

### 2.2 不要围绕大模型设计系统

大模型只是分析与编排层，不是系统核心。

正确关系：

```txt
数据源 → 事件归一 → Incident → RCA → AI 总结 → Runbook → 自动化执行
```

错误关系：

```txt
用户 → 大模型 → 数据库 / 服务器 / Ansible
```

AI 不允许直接操作生产资源。所有执行动作必须经过：

```txt
AI 建议
  ↓
Policy Engine
  ↓
人工审批 / 策略审批
  ↓
AutomationJob
  ↓
aiops-runner 执行
  ↓
审计日志
```

---

### 2.3 模块化单体优先，不要过早微服务

MVP 阶段只允许三个后端应用：

```txt
apps/
  aiops-server   # API 服务
  aiops-worker   # 异步任务与分析服务
  aiops-runner   # 自动化执行服务
```

不要在 MVP 阶段拆成：

```txt
auth-service
asset-service
alert-service
incident-service
ai-service
automation-service
```

除非有明确的性能或部署边界需求，否则保持模块化单体。

---

### 2.4 数据源、分析器、执行器必须解耦

Zabbix、VictoriaMetrics、ClickHouse、LLM、Ansible 都是外部能力。

必须通过 Adapter / Client / Provider 层接入。

禁止业务逻辑直接调用外部 API。

正确示例：

```txt
aiops-zabbix-adapter
aiops-vm-adapter
aiops-clickhouse-adapter
aiops-ai
aiops-automation
```

错误示例：

```java
// Controller 里直接调用 Zabbix HTTP API
// Service 里直接拼接 ClickHouse SQL
// AI 直接调用 Ansible
```

---

## 3. 技术栈

### 3.1 前端

```txt
React
Vite
TypeScript
shadcn/ui
Tailwind CSS
TanStack Query
TanStack Table
ECharts
React Flow
Monaco Editor
SSE / WebSocket
```

前端页面优先级：

```txt
1. 登录页
2. 总览 Dashboard
3. 数据源管理
4. 资产管理
5. 告警中心
6. Incident 中心
7. Incident 详情页
8. AI 诊断面板
9. Runbook 管理
10. AutomationJob 审批与执行日志
11. 审计日志
```

---

### 3.2 后端

```txt
Java 21
Spring Boot 3.x / 4.x
Spring MVC
Spring Security
JWT
RBAC
MyBatis-Flex 或 jOOQ
PostgreSQL
Redis
ClickHouse JDBC
VictoriaMetrics HTTP API
MinIO Java SDK
OpenAPI / springdoc-openapi
Micrometer
OpenTelemetry Java Agent
```

MVP 优先选择：

```txt
Java 21
Spring Boot 3.x
MyBatis-Flex
PostgreSQL
Redis
ClickHouse
VictoriaMetrics
MinIO
```

---

### 3.3 存储职责

```txt
PostgreSQL:
  用户、租户、权限、资产、数据源、告警索引、Incident、Runbook、AutomationJob、审计日志

Redis:
  缓存、队列、分布式锁、临时上下文、SSE 状态

VictoriaMetrics:
  CPU、内存、磁盘、网络、QPS、错误率、延迟等时序指标

ClickHouse:
  日志、事件、RUM 明细、Zabbix event 明细、Incident timeline 明细

MinIO:
  附件、诊断报告、sourcemap、session replay、Ansible 执行产物

pgvector:
  MVP 阶段用于历史事故、Runbook、知识文档的向量检索

Milvus:
  后期大规模向量数据再引入
```

---

## 4. 推荐仓库结构

```txt
aegisops/
├─ pom.xml
├─ apps/
│  ├─ aiops-server/
│  ├─ aiops-worker/
│  └─ aiops-runner/
│
├─ modules/
│  ├─ aiops-common/
│  ├─ aiops-web/
│  ├─ aiops-security/
│  ├─ aiops-tenant/
│  ├─ aiops-user/
│  ├─ aiops-datasource/
│  ├─ aiops-asset/
│  ├─ aiops-alert/
│  ├─ aiops-incident/
│  ├─ aiops-rca/
│  ├─ aiops-ai/
│  ├─ aiops-runbook/
│  ├─ aiops-automation/
│  ├─ aiops-audit/
│  ├─ aiops-notification/
│  ├─ aiops-zabbix-adapter/
│  ├─ aiops-vm-adapter/
│  ├─ aiops-clickhouse-adapter/
│  ├─ aiops-otel-adapter/
│  └─ aiops-rum/
│
├─ web/
│  └─ console/
│
├─ infra/
│  ├─ docker-compose.yml
│  ├─ postgres/
│  ├─ redis/
│  ├─ clickhouse/
│  ├─ victoria-metrics/
│  ├─ minio/
│  └─ zabbix/
│
└─ docs/
   ├─ architecture.md
   ├─ data-model.md
   ├─ rca-design.md
   ├─ ai-agent-design.md
   ├─ automation-safety.md
   └─ mvp-roadmap.md
```

---

## 5. 应用职责

### 5.1 aiops-server

对前端提供 API。

职责：

```txt
用户登录
租户管理
RBAC 权限
数据源配置
资产查询
告警查询
Incident 查询与状态流转
AI 诊断触发
Runbook 管理
AutomationJob 审批
审计日志查询
SSE / WebSocket 推送
```

禁止：

```txt
直接执行 Ansible
直接执行 SSH
直接执行高风险 shell
直接绕过 worker 进行复杂异步分析
```

---

### 5.2 aiops-worker

负责异步任务、数据清洗和分析。

职责：

```txt
同步 Zabbix 主机
同步 Zabbix problem/event
消费 Redis Stream 事件
AlertEvent 去重
AlertEvent 聚合
生成 Incident
更新 Incident timeline
查询 VictoriaMetrics 指标上下文
查询 ClickHouse 日志/事件上下文
执行 RCA 规则
调用 AI Orchestrator 生成诊断
生成复盘草稿
发送通知
```

---

### 5.3 aiops-runner

负责自动化执行。

职责：

```txt
执行 Ansible Playbook
执行 SSH Runner
执行 Webhook Runner
执行 K8s Runner，后期
记录执行日志
控制超时
执行前权限校验
执行后健康检查
回写 AutomationJob 状态
写入 audit_log
```

必须独立部署，不能和 `aiops-server` 混在一起。

---

## 6. 核心领域模型

### 6.1 Asset

资产是所有告警和事故的挂载对象。

资产类型：

```txt
host
service
endpoint
database
redis
middleware
application
page
tenant
k8s_cluster
k8s_namespace
k8s_pod
```

核心字段：

```txt
id
tenant_id
asset_type
name
display_name
source
source_id
env
ip
tags
status
created_at
updated_at
```

---

### 6.2 AssetRelation

用于表达资产拓扑。

关系类型：

```txt
depends_on
runs_on
contains
calls
owns
related_to
```

核心字段：

```txt
id
tenant_id
from_asset_id
to_asset_id
relation_type
confidence
source
created_at
updated_at
```

---

### 6.3 AlertEvent

统一告警事件。

来源可以是：

```txt
zabbix
prometheus
rum
opentelemetry
webhook
manual
```

核心字段：

```txt
id
tenant_id
source
source_event_id
severity
title
description
asset_id
entity_type
entity_name
labels
starts_at
ends_at
status
raw_payload
fingerprint
created_at
```

fingerprint 用于去重。

推荐生成规则：

```txt
source + asset_id + source_trigger_id + normalized_title
```

---

### 6.4 Incident

事故是核心对象。

核心字段：

```txt
id
tenant_id
title
summary
severity
status
source
primary_asset_id
suspected_root_cause
confidence
impact_score
started_at
detected_at
resolved_at
owner_user_id
created_at
updated_at
```

状态：

```txt
open
investigating
mitigating
resolved
closed
ignored
```

---

### 6.5 IncidentEvent

用于关联 Incident 和各种事件。

事件类型：

```txt
alert
metric_anomaly
log_error
change
automation
ai_diagnosis
manual_note
recovery
```

关系类型：

```txt
primary
related
upstream
downstream
evidence
noise
```

---

### 6.6 IncidentTimeline

事故详情页必须以时间线组织。

时间线事件包括：

```txt
告警产生
指标异常
日志异常
变更事件
AI 诊断
人工备注
Runbook 执行
恢复事件
关闭事故
```

---

### 6.7 DiagnosisResult

AI 诊断结果必须结构化保存。

字段：

```txt
summary
severity
suspected_root_cause
confidence
evidence[]
impact
suggestions[]
created_at
model_provider
model_name
```

示例：

```json
{
  "summary": "主机 CPU 持续升高并触发 Zabbix 告警",
  "severity": "high",
  "suspectedRootCause": "疑似某 Java 进程 CPU 占用异常",
  "confidence": 0.78,
  "evidence": [
    {
      "type": "metric",
      "description": "CPU 使用率在 10 分钟内从 35% 升至 95%"
    },
    {
      "type": "alert",
      "description": "Zabbix trigger: CPU high 已触发"
    }
  ],
  "suggestions": [
    {
      "title": "执行主机基础巡检 Runbook",
      "risk": "low",
      "requiresApproval": false
    }
  ]
}
```

---

## 7. RCA 设计原则

MVP 阶段使用：

```txt
规则 RCA + 证据链 + AI 总结
```

不要一开始训练模型。

第一版 RCA 规则：

```txt
R1: 异常前后 30 分钟是否存在发布/变更
R2: 同一资产是否连续产生多个告警
R3: 上游资产告警是否早于下游资产
R4: 错误是否集中在某个 host / service / version
R5: 指标异常是否和日志错误时间重合
R6: 是否命中历史相似事故
R7: 是否命中已有 Runbook
R8: 是否属于告警风暴中的重复告警
```

每条规则必须输出：

```txt
rule_id
score
evidence
related_asset_id
related_event_id
```

AI 只能基于证据链总结，不允许编造不存在的数据。

---

## 8. AI Agent 设计原则

### 8.1 AI 可以做什么

```txt
总结事故
解释证据链
生成排障步骤
推荐 Runbook
匹配历史事故
生成复盘草稿
生成查询语句
输出风险提示
```

### 8.2 AI 不能直接做什么

```txt
直接执行 SSH
直接执行 Ansible
直接删除文件
直接回滚生产
直接修改配置
直接重启数据库
直接关闭核心服务
```

### 8.3 Tool 白名单

允许注册以下工具：

```txt
queryMetrics
queryLogs
queryAlerts
queryAssets
queryTopology
queryChanges
searchRunbooks
searchSimilarIncidents
generateIncidentReport
recommendRunbook
proposeAutomation
```

执行类动作必须统一转为：

```txt
proposeAutomation
```

然后交给 `aiops-automation` 和 `aiops-runner`。

---

## 9. 自动化安全原则

自动化必须遵守风险分级。

### 9.1 低风险

```txt
查询日志
查询指标
主机巡检
创建工单
发送通知
读取进程列表
读取磁盘空间
读取服务状态
```

可不审批，但必须记录审计。

### 9.2 中风险

```txt
重启无状态服务
清理临时目录
刷新缓存
扩容副本
```

默认需要审批。

### 9.3 高风险

```txt
回滚版本
修改配置
切流
重启数据库
删除文件
```

必须审批，必须有回滚方案，必须写入审计。

### 9.4 禁止默认自动化

```txt
rm -rf
drop database
truncate table
删除 K8s namespace
停止核心中间件
修改防火墙
清空 Redis
格式化磁盘
```

除非后续显式设计安全沙箱和多级审批，否则禁止实现。

---

## 10. API 设计约定

REST API 起步。

认证：

```txt
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

数据源：

```txt
GET  /api/datasources
POST /api/datasources
POST /api/datasources/{id}/test
POST /api/datasources/{id}/sync
```

资产：

```txt
GET /api/assets
GET /api/assets/{id}
GET /api/assets/{id}/relations
```

告警：

```txt
GET  /api/alerts
GET  /api/alerts/{id}
POST /api/alerts/{id}/ignore
```

事故：

```txt
GET  /api/incidents
POST /api/incidents
GET  /api/incidents/{id}
GET  /api/incidents/{id}/timeline
POST /api/incidents/{id}/resolve
POST /api/incidents/{id}/close
```

AI 诊断：

```txt
POST /api/incidents/{id}/diagnose
GET  /api/incidents/{id}/diagnoses
POST /api/ai/chat
POST /api/ai/chat/stream
```

Runbook：

```txt
GET  /api/runbooks
POST /api/runbooks
GET  /api/runbooks/{id}
POST /api/runbooks/{id}/execute
```

自动化：

```txt
GET  /api/automation/jobs
POST /api/automation/jobs
POST /api/automation/jobs/{id}/approve
POST /api/automation/jobs/{id}/cancel
GET  /api/automation/jobs/{id}/logs
```

AI 流式输出优先使用 SSE。

---

## 11. MVP Roadmap

### Phase 0: 工程地基

目标：

```txt
打通项目骨架、基础设施、登录权限、数据库迁移。
```

交付：

```txt
Maven 多模块项目
aiops-server / aiops-worker / aiops-runner
React 控制台
Docker Compose
PostgreSQL / Redis / ClickHouse / VictoriaMetrics / MinIO
Flyway 迁移
Spring Security + JWT
基础 RBAC
OpenAPI
健康检查
```

验收：

```txt
本地一键启动
可以登录
可以访问 API 文档
可以创建租户、用户、角色
可以看到空 Dashboard
```

---

### Phase 1: Zabbix 接入

目标：

```txt
把 Zabbix 作为第一个数据源接入。
```

交付：

```txt
数据源管理页面
Zabbix 连接配置
Zabbix API 测试连接
同步 host / host group
同步 trigger / problem / event
转换为 Asset / AlertEvent
告警列表
资产列表
```

验收：

```txt
配置 Zabbix 地址和 token 后，可以同步主机与告警。
Zabbix 中出现 problem 后，平台能看到 AlertEvent。
```

---

### Phase 2: Incident 事故中心

目标：

```txt
从告警列表升级为事故中心。
```

交付：

```txt
Alert fingerprint 去重
告警聚合规则
Incident 自动创建
Incident 详情页
Incident 时间线
告警与事故关联
严重级别计算
事故状态流转
```

验收：

```txt
多条重复告警不会淹没页面，而是聚合成一个 Incident。
Incident 页面能看到相关告警、时间线、资产信息。
```

---

### Phase 3: RCA 规则引擎

目标：

```txt
Incident 详情页自动生成证据链。
```

交付：

```txt
VictoriaMetrics 查询封装
指标查询工具
事故相关指标面板
RCA 规则引擎
证据链模型
初版根因评分
RCA 结果展示
```

验收：

```txt
进入 Incident 后，系统能自动查询事故前后 30 分钟指标，并生成初步 RCA 证据。
```

---

### Phase 4: AI 诊断助手

目标：

```txt
把 RCA 证据交给 AI，生成可解释诊断报告。
```

交付：

```txt
LLM Provider 抽象
至少接入一个模型供应商
AI Tool Registry
事故诊断 Prompt
结构化 DiagnosisResult
AI 诊断页面
SSE 流式输出
诊断结果保存
```

验收：

```txt
点击“AI 诊断”后，可以生成包含证据链、根因推测、处理建议的报告。
```

---

### Phase 5: Runbook 与 Ansible 执行

目标：

```txt
从分析问题进入辅助处理问题。
```

交付：

```txt
Runbook 管理
Runbook Step 设计
Ansible Runner 接入
AutomationJob 模型
审批流
执行日志实时输出
执行结果回写 Incident
```

内置 Runbook：

```txt
主机基础巡检
磁盘空间检查
Nginx 状态检查
Java 进程检查
服务重启，需审批
```

验收：

```txt
Incident 页面可以推荐 Runbook。
用户确认后可以执行巡检类 Ansible Playbook。
执行日志能实时显示，并写入事故时间线。
```

---

### Phase 6: 复盘与知识沉淀

目标：

```txt
让每次事故都变成后续 AI 可用的知识。
```

交付：

```txt
事故复盘报告生成
历史事故库
Runbook 与事故关联
pgvector 知识检索
相似事故搜索
AI 根据历史事故增强诊断
```

验收：

```txt
关闭 Incident 时自动生成复盘草稿。
下次类似告警出现时，AI 能提示历史相似事故。
```

---

## 12. MVP 不做的内容

以下内容不要在 MVP 阶段实现：

```txt
完整 Prometheus 替代
完整日志平台
完整链路追踪平台
复杂 K8s Operator
多地域高可用
复杂 CMDB
复杂大屏
完全自动修复
训练自己的大模型
全量 Milvus
全量 Kafka
过早微服务拆分
复杂 License 系统
```

---

## 13. 后续版本方向

### V0.2: RUM + Release + Sourcemap

```txt
前端 SDK
JS Error
Resource Error
API Error
白屏检测
Release 注入
Sourcemap 上传
源码定位
AI 前端故障诊断
```

### V0.3: OpenTelemetry 接入

```txt
OTel Collector
Trace 接入
Service 拓扑
接口级 RCA
后端链路分析
```

### V0.4: 告警降噪增强

```txt
拓扑关联
告警风暴合并
影响面计算
相似事故聚类
异常检测
```

### V0.5: 企业化

```txt
LDAP / OAuth
审计增强
私有化部署脚本
Helm Chart
备份恢复
License
团队协作
```

---

## 14. 编码规范

### 14.1 Java

要求：

```txt
使用 Java 21
Controller 不写业务逻辑
Service 不直接访问外部系统
外部系统必须通过 Adapter/Client
DTO、Entity、VO 分层
核心领域逻辑放在 domain/service 层
异常必须结构化
日志必须包含 traceId / tenantId / userId
```

禁止：

```txt
Controller 直接写 SQL
Controller 直接调用 Zabbix
Controller 直接调用 Ansible
Service 里硬编码租户 ID
全局吞异常
返回 Map<String, Object> 作为主要 API 类型
```

---

### 14.2 TypeScript / React

要求：

```txt
使用 TypeScript
使用 TanStack Query 管理服务端状态
表单优先使用受控 schema
复杂表格使用 TanStack Table
图表封装为业务组件
API 类型从 OpenAPI 生成或统一维护
```

禁止：

```txt
页面里直接拼接大量 API 路径
页面里直接写复杂数据转换
any 滥用
重复封装相同请求逻辑
```

---

## 15. 测试要求

每个 Phase 至少需要：

```txt
核心 Service 单元测试
Adapter 集成测试，允许 mock 外部系统
Controller API 测试
关键前端页面 smoke test
Docker Compose 启动验证
```

核心测试优先级：

```txt
Alert fingerprint 去重
Alert → Incident 聚合
RCA 规则评分
AI 结构化输出解析
AutomationJob 状态流转
权限校验
```

---

## 16. Agent 工作方式

AI 编码 Agent 在执行任务时必须：

````txt
1. 先阅读本 AGENTS.md
2. 明确当前属于哪个 Phase
3. 不越级实现后续阶段功能
4. 优先补全领域模型和测试
5. 修改外部接口时同步更新 OpenAPI
6. 修改数据库结构时新增 migration
7. 修改安全相关代码时补充审计和权限判断
8. 不引入不必要的大型依赖
9. 不把 MVP 复杂化
10. 不绕过 aiops-runner 执行自动化动作
11. 与用户交互、撰写文档、提交说明、代码注释、PR 描述默认使用中文；除非用户明确要求其他语言或上下文必须使用英文（例如公开协议、外部 SDK API、国际化文案）
12. 写前端 UI 前先 `pnpm dlx shadcn@latest add` 拉取组件，禁止自封 div + 颜色 class 拼 UI（详见第 21 节）
13. 改完代码必须跑对应工程的 `lint` / `format` / `test` / `build` 四件套并自检通过，提交前不得有未处理告警
14. 严禁在主分支或 phase 分支上直接 push；所有变更走 Pull Request，PR 至少需要 1 名 Owner 审阅通过

---

## 17. Git 提交与变更规范

所有 Commit Message、PR 标题、PR 描述、变更日志必须遵循以下规范。**违反规范的提交会被打回重写。**

### 17.1 格式

```txt
<type>(<scope>): <中文主题>

<中文正文，列点说明动机与变更点>

<可选 Footer，英文关键字>
````

### 17.2 主题行（首行）

```txt
type    : 必填，小写，固定枚举
scope   : 必填，小写，固定枚举；多模块用逗号分隔
冒号    : 半角英文冒号 + 一个半角空格
主题    : 中文，祈使句，「动词 + 名词」结构，不加句号，不超过 50 个汉字
```

#### 17.2.1 type 枚举

```txt
feat     新功能、新接口、新页面
fix      缺陷修复
refactor 重构，不改变行为
perf     性能优化
test     补齐/调整测试
docs     文档、设计、ADR
build    构建脚本、依赖、版本
ci       CI / CD 流水线
infra    docker-compose、部署脚本、基础设施
db       数据库迁移、Schema 调整
chore    杂项（拼写、注释、目录调整）
revert   回滚
```

#### 17.2.2 scope 枚举

按本项目实际结构收敛，新增模块时再扩展：

```txt
# 后端应用
server, worker, runner

# 后端模块
common, web, security, tenant, user, datasource, asset, alert,
incident, rca, ai, runbook, automation, audit, notification,
zabbix-adapter, vm-adapter, clickhouse-adapter, otel-adapter, rum

# 前端
console

# 基础设施与跨切
infra, db, deps, config

# 元数据
docs, agent, governance
```

#### 17.2.3 主题行示例

```txt
feat(incident): 新增按时间桶聚合策略
fix(alert): 修复 fingerprint 为空时重复入库
refactor(server): 将 tenant 过滤下沉到 Repository
perf(datasource): 同步主机时使用批量 upsert
docs(mvp): 补充 Phase 2 设计与验证步骤
db(incident): 新增 V4 事故聚合迁移
infra(compose): 引入 VictoriaMetrics 与 MinIO
```

### 17.3 正文

```txt
- 必填，使用中文，列点说明
- 每条以「模块/动作」开头，例如「后端:」「前端:」「迁移:」
- 聚焦「为什么」与「影响」，不要复述 diff
- 涉及外部接口变更必须显式写「接口:」
- 涉及数据库变更必须显式写「迁移:」并指明版本号
- 涉及安全、权限、审计必须显式写「安全:」
- 总长度建议控制在 30 行内
```

#### 17.3.1 正文示例

```txt
feat(incident): 新增按时间桶聚合策略

后端:
- IncidentService 引入 TimeBucketPolicy，key = asset_id + severity，
  默认 30 分钟；命中已开事故时只追加 incident_alert。
- 同桶内告警数超过阈值时按严重度升级事故（warning → critical）。
- 新增 IncidentAggregationPolicy 配置类，支持按租户覆盖。

迁移:
- V4__phase2_incident_aggregation.sql：incidents、incident_alerts、
  incident_timeline 三张表，tenant_id 必填。

接口:
- POST /api/incidents/aggregate
- GET  /api/incidents/{id}/timeline
- POST /api/incidents/{id}/status

安全:
- 所有接口强制 tenant 过滤，复用 TenantContext
```

### 17.4 Footer

```txt
可选项；用于自动化与关联追踪，关键字使用英文半角
Refs:    #关联 issue / 文档（不关闭）
Closes:  #关闭 issue
Breaks:  #破坏性变更，必须列出影响面
Refs-Tests:  #测试覆盖说明
```

示例：

```txt
Refs: docs/mvp/design/phase2.md
Closes: #42
Breaks: /api/incidents 响应增加 severity 字段
```

### 17.5 PR 规范

```txt
标题：与提交主题行同格式，例 feat(incident): 新增按时间桶聚合策略
描述：必须包含
  1. 背景（为什么）
  2. 主要变更（列点）
  3. 验证方式（curl / SQL / 截图 / 录屏）
  4. 风险与回滚
  5. 关联 issue / 文档
合并：仅允许 squash merge 或 rebase merge；merge commit 会污染主线历史
```

### 17.6 反例（禁止写法）

```txt
# 1. 没有 type 和 scope
add zabbix integration

# 2. 主题用英文
feat(incident): add time bucket policy

# 3. 主题过长，超过 50 字
feat(incident): 新增按时间桶聚合策略并支持严重度自动升级以及自定义租户配置

# 4. scope 自由发挥
feat(my-module): xxx

# 5. 多件事塞一个提交
feat(server, worker, runner): 重构、重写、修复若干问题
```

### 17.7 Agent 自检清单

每次准备 `git commit` 之前必须确认：

```txt
[ ] 首行符合 <type>(<scope>): 中文主题 格式
[ ] type 在固定枚举内
[ ] scope 在固定枚举内
[ ] 主题 ≤ 50 字，无句号
[ ] 正文列点，每点带「模块/动作」前缀
[ ] 涉及接口变更已写「接口:」
[ ] 涉及数据库变更已写「迁移:」并指明版本号
[ ] 涉及安全变更已写「安全:」
[ ] Footer 关键字使用英文
[ ] 提交前已跑过 mvn verify 或对应模块的测试
```

````

---

## 18. 当前最优先任务

当前 MVP 起步时，Agent 应按以下顺序推进：

```txt
1. 初始化 Maven 多模块
2. 初始化 React 控制台
3. 编写 docker-compose
4. 接入 PostgreSQL / Redis / ClickHouse / VictoriaMetrics / MinIO
5. 建立 Flyway migration
6. 实现 auth / tenant / user / role
7. 实现 datasource 模块
8. 实现 Zabbix Adapter
9. 同步 Zabbix host / problem
10. 建立 Asset / AlertEvent
11. 实现 Alert 列表
12. 实现 Incident 聚合
13. 实现 Incident 详情和 timeline
14. 实现 RCA 规则
15. 实现 AI 诊断
16. 实现 Runbook 和 Ansible Runner
````

---

## 19. 最终验收 Demo

MVP 完成后必须能演示：

```txt
1. 用户登录控制台
2. 添加 Zabbix 数据源
3. 同步主机和告警
4. Zabbix 触发 CPU 告警
5. 平台自动生成 Incident
6. Incident 页面展示告警、资产、指标趋势、时间线
7. 点击 AI 诊断
8. AI 输出带证据链的诊断报告
9. 系统推荐主机巡检 Runbook
10. 用户确认执行
11. Ansible Runner 执行巡检
12. 实时显示执行日志
13. 执行结果写入 Incident 时间线
14. 关闭 Incident
15. 自动生成复盘报告
```

---

## 20. 最高优先级提醒

永远优先保证：

```txt
事件归一
Incident 模型
证据链
自动化安全边界
审计
MVP 闭环
```

不要优先追求：

```txt
酷炫大屏
复杂 AI Agent
过早微服务
复杂拓扑图
完全自动修复
全量采集平台
```

---

## 21. 工程纪律与代码质量

本节是横切规则，优先级高于个人风格偏好。**违反本节的代码必须打回。**

### 21.1 Lint / Format / Typecheck / Test / Build 五件套

所有工程必须配置并跑通以下五件套，提交前不得有任何一项失败。

| 工程                         | 类型检查                     | 静态检查                             | 格式化   | 测试          | 构建                         |
| ---------------------------- | ---------------------------- | ------------------------------------ | -------- | ------------- | ---------------------------- |
| `apps/*` `modules/*`（Java） | `mvn -q -DskipTests compile` | Spotless + Checkstyle（见 21.2）     | Spotless | `mvn -q test` | `mvn -q -DskipTests package` |
| `web/console`（TS/React）    | `pnpm exec tsc -b`           | ESLint + `eslint-plugin-tailwindcss` | Prettier | `pnpm test`   | `pnpm build`                 |
| `infra/`                     | —                            | `docker compose config`              | —        | —             | `docker compose build`       |

四件套脚本统一收敛在根 `package.json` 的 `scripts`：

```jsonc
{
  "scripts": {
    "lint": "pnpm -r --parallel run lint",
    "format": "pnpm -r --parallel run format",
    "typecheck": "pnpm -r --parallel run typecheck",
    "test": "pnpm -r --parallel run test",
    "build": "pnpm -r --parallel run build",
  },
}
```

CI 流水线（`.github/workflows/ci.yml`）必须串行执行 `lint → typecheck → test → build`，任一失败即阻断合并。

### 21.2 后端代码质量硬要求

```txt
- Spotless 强制格式：2 空格缩进、UTF-8、LF 行尾、去除尾部空白；import 按字母序
- Checkstyle 规则：方法 ≤ 80 行、类 ≤ 500 行、参数列表 ≤ 5 个、嵌套深度 ≤ 4
- 强制开启的 Spotbugs 规则：EI_EXPOSE_REP、SQL_INJECTION、REC_CATCH_EXCEPTION
- 公共 API 类必须有 Javadoc；领域 Service 方法描述业务意图而非实现
- 异常必须继承 AiopsException 子类，禁止裸 throw new RuntimeException
- 日志格式：MDC 必须含 traceId / tenantId / userId / requestPath，缺失即告警
- 业务包禁止依赖 org.springframework.web；org.springframework.web 只能出现在 controller / filter / config 层
- Repository / Mapper 不允许返回 Map<String,Object>，必须用 Entity / DTO
- 任何跨模块调用必须经过 Application Service，禁止 Module A 直接注入 Module B 的 Repository
- 任何外部系统（Zabbix、VM、ClickHouse、LLM、Ansible）调用必须经过 aiops-*-adapter 抽象，禁止 Service 直接 HttpClient
```

### 21.3 前端代码质量硬要求

```txt
- ESLint 必须开启的规则集：
  - @typescript-eslint/no-explicit-any            error
  - @typescript-eslint/no-unused-vars             error (忽略 _ 前缀)
  - @typescript-eslint/consistent-type-imports    error
  - react-hooks/rules-of-hooks                    error
  - react-hooks/exhaustive-deps                   error
  - tailwindcss/classnames-order                  warn
  - tailwindcss/no-custom-classname               error  // 配合 21.4 强制语义化
  - import/order                                 warn
- Prettier：单引号、printWidth 100、trailingComma all、semi false（与 Vite 模板一致）
- 任何 src/**/*.tsx 不允许出现以下自封模式：
  - 散落的 className="rounded-2xl bg-white p-6 shadow-sm" 等手搓卡片
  - 散落的 className="rounded-full border bg-xxx text-xxx" 手搓徽章
  - className="space-y-*" / "space-x-*"  // 一律改为 flex + gap-*
  - 自定义 <Field>、<StatusChip>、<EmptyState> 等与 shadcn 等价的私有组件
- 新页面必须先 `pnpm dlx shadcn@latest add` 再写代码；如确认 shadcn 暂无对应组件，必须在本节末位追加「本项目 shadcn 缺口」清单
- API 客户端类型必须从 `src/api/client.ts` 集中维护或由 OpenAPI 生成；禁止页面里散落手写 DTO interface
- Hook 命名以 use 开头；超过 80 行或包含多步副作用的 Hook 必须拆为 useXxx + useXxxMutation 配对
```

### 21.4 Tailwind / shadcn 强制规则

```txt
- 颜色：必须用 bg-primary / text-muted-foreground / ring 等语义 token；禁止 bg-blue-500、text-slate-600 这类 raw 颜色
- 间距：使用 gap-*；禁止 space-y-* / space-x-*
- 等宽高：使用 size-*；禁止 w-10 h-10 同时出现
- 截断：使用 truncate；禁止手写 overflow-hidden text-ellipsis whitespace-nowrap
- 暗色：禁止手动 dark: 覆盖颜色；通过 .dark 父级 + 语义 token 自动生效
- 条件类：必须用 cn() 工具函数；禁止手写三元 template literal
- z-index：shadcn 已内置的 Dialog/Sheet/Popover/Tooltip 禁止再覆盖 z-index
- 按钮：使用 Button 组件 + variant；禁止 <button className="rounded-lg bg-indigo-600 ..."> 自封
- 徽章：使用 Badge 组件；禁止 <span className="rounded-full bg-rose-100 ..."> 自封
- 卡片：使用 Card + CardHeader + CardTitle + CardDescription + CardContent + CardFooter 完整组合；禁止 <div className="rounded-2xl bg-white shadow-sm"> 简化版
- 表单：使用 FieldGroup + Field + FieldLabel + FieldDescription + FieldError；禁止 <label className="block"><span>Label</span><input /></label> 自封
- 校验：Field 写 data-invalid，控件写 aria-invalid；FieldSet + FieldLegend 用于分组
- 图标：使用 lucide-react；Button 内的 icon 必须用 data-icon="inline-start" / data-icon="inline-end"，不允许手写 size-4
- 空态：使用 Empty + EmptyMedia + EmptyTitle + EmptyDescription；禁止 <div className="text-center text-slate-500">No data</div>
- 加载占位：使用 Skeleton；禁止 <div className="animate-pulse bg-slate-200" />
- 反馈：Toast 统一走 sonner 的 toast()；禁止 <div className="absolute top-2 right-2 bg-emerald-500">Saved</div>
```

### 21.5 安全与多租户

```txt
- 任何 HTTP 出口必须设置 connectTimeout 与 readTimeout，缺省值 ≤ 10s
- 任何写接口必须经 TenantGuard / PermissionGuard 双层校验；缺一即不合规
- 写操作必须经 AuditLogger 落库 audit_log；缺失即视为绕过审计
- 自动化执行类动作必须走 aiops-runner；禁止 aiops-server / aiops-worker 直接 SSH / Ansible
- 凭据类字段（password、apiToken、secret）禁止写入普通日志；Logback Filter 必须 mask
- 导出文件 / 上传文件必须经过 mime + size + name 校验，禁止前端单点校验
```

### 21.6 依赖与版本

```txt
- 新增依赖前必须经 Owner 评审；同一类需求已有依赖时不得引入竞品
- 禁止引入：lodash（全量）、moment（请用 dayjs / date-fns）、@ant-design/*、element-plus（与 shadcn 冲突）、nivo、bizcharts（统一 ECharts）
- 锁文件必须提交；不得出现 package-lock.json + pnpm-lock.yaml + yarn.lock 并存
- 升级主版本（major）必须单列 PR，PR 描述需写明 Breaking 影响面
- 内部模块之间禁止循环依赖；arch-unit 或自定义脚本必须每 CI 跑一次
```

### 21.7 测试与覆盖率

```txt
- 单元测试覆盖率门槛：domain/service 层 ≥ 80%，controller 层 ≥ 60%
- 新增 Service 公共方法必须含至少 1 个单测：覆盖正常路径 + 至少 1 个异常路径
- Adapter 必须含集成测试，允许 mock 外部 HTTP；测试用例至少覆盖：成功、超时、4xx、5xx
- 前端关键页面必须含 1 个 smoke test：组件挂载 + 核心交互至少 1 次
- Bug 修复必须先写复现单测再修复；修复后单测必须先红后绿
```

### 21.8 AI Agent 自检清单（提交前必走）

```txt
[ ] 第 16 节 14 条全部满足
[ ] 第 17 节 commit 规范自检 10 条全部勾选
[ ] 第 21.1 节五件套全部通过，CI 全绿
[ ] 第 21.3 节自封模式 grep 0 命中
[ ] 第 21.4 节 raw 颜色 / space-y-* / 自封按钮 grep 0 命中
[ ] 第 21.5 节审计 / 凭据 mask 兜底存在
[ ] 第 21.7 节新方法有单测、Bug 修复有复现单测
[ ] PR 描述包含：背景 / 主要变更 / 验证方式 / 风险与回滚 / 关联 issue
[ ] 至少 1 名 Owner 审阅通过
```

### 21.9 本项目 shadcn 缺口

记录经评审确认 shadcn 暂无等价、必须自封的组件。**新增条目需 Owner 同意。**

```txt
（暂无）
```

---

本项目真正有价值的不是“接入了多少数据源”，而是：

> 能否把一次线上故障从发现、定位、处置到复盘真正串起来。
