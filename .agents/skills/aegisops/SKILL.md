---
name: aegisops
description: 在 AegisOps/FaultLens AI Ops 项目中工作时使用本 Skill。它指导 Agent 实现一个基于 Java Spring Boot 的 AIOps 平台,涵盖 Zabbix 数据接入、Incident 聚合、RCA 证据链、AI 诊断、Runbook 推荐、Ansible 自动化、审计日志以及 MVP 阶段的纪律。适用于架构设计、代码生成、重构、评审、测试、数据库设计、前端控制台、后端模块、Worker 任务、Runner 安全以及路线图执行。
---

# AegisOps / FaultLens 项目 Skill

本 Skill 定义 AegisOps / FaultLens 项目的产品方向、架构规则、代码边界、MVP 路线图与安全要求。

在以下场景使用本 Skill:

```txt
- 设计架构
- 编写后端代码
- 编写前端代码
- 设计数据库 Schema
- 接入 Zabbix
- 实现 Incident 聚合
- 实现 RCA 逻辑
- 实现 AI 诊断
- 实现 Runbook / Ansible 自动化
- 评审项目代码
- 规划 MVP Phase
- 生成测试
- 重构模块
- 编写文档
- 为 Codex / Cursor / Claude Code 拆分任务
```

---

## 1. 产品定位

项目名称:

```txt
平台名称: AegisOps
MVP / 聚焦产品名称: FaultLens
```

产品定位:

```txt
AI 驱动的可观测、智能排障与受控自动化平台。
```

MVP 必须聚焦以下闭环:

```txt
Zabbix 告警
  ↓
AlertEvent 归一
  ↓
Incident 聚合
  ↓
指标 / 日志 / 资产 / 时间线上下文采集
  ↓
RCA 证据链
  ↓
AI 诊断
  ↓
Runbook 推荐
  ↓
人工审批后的 Ansible 执行
  ↓
执行结果回写
  ↓
复盘报告
  ↓
历史知识复用
```

不要把产品做成通用大屏或通用 AI 聊天应用。

---

## 2. 最高级原则

始终围绕 `Incident` 进行设计。

核心领域模型是:

```txt
Incident
```

而不是:

```txt
LLM 聊天
单条告警
单条指标
大屏组件
原始 Zabbix 问题
Ansible 脚本
```

在新增任何功能之前先问:

```txt
这能帮助检测、理解、处置或复盘 Incident 吗?
```

如果答案是否, 延后实现。

---

## 3. 架构原则

### 3.1 以模块化单体起步

MVP 必须使用:

```txt
apps/
  aiops-server        # 控制面 / REST API / SSE / 权限
  aiops-worker        # 异步消费 / Outbox / 后台分析
  aiops-runner        # 执行隔离层 / Ansible / SSH / Webhook

apps/aiops-agent      # Python LangGraph 诊断运行时, 与 Java 通过 HTTP + internal token 解耦
```

后端架构定性为:

```txt
模块化单体 (Java) + 外挂 Python Agent + 独立 Runner
```

不是微服务。任何把 server/worker/runner 描述为"独立服务"或"分布式系统"的设计评审视为不通过。

三 app 与 agent 共享同一份数据库 schema (Flyway), 但运行时独立部署、独立进程、独立端口 (端口表见 `references/architecture-boundaries.md` §1)。

MVP 阶段不要拆分为微服务。

也要避免过早出现:

```txt
auth-service
asset-service
alert-service
incident-service
ai-service
automation-service
```

先模块化单体, 后续再拆服务。

---

### 3.2 三个后端应用

#### aiops-server

职责:

```txt
REST API
SSE 流式响应
认证
鉴权
租户管理
用户管理
数据源管理
资产查询
告警查询
Incident 查询
AI 诊断触发
Runbook 管理
AutomationJob 审批
审计查询
面向前端 API
```

禁止:

```txt
直接执行 Shell
直接执行 Ansible
直接执行 SSH
直接执行高危自动化
长时间数据同步
大批量分析
```

---

#### aiops-worker

职责:

```txt
Zabbix 主机同步
Zabbix 问题/事件同步
AlertEvent 归一
AlertEvent 指纹
告警去重
Incident 聚合
Incident 时间线生成
指标上下文采集
日志/事件上下文采集
RCA 规则执行
AI 诊断任务执行
复盘初稿生成
通知下发
```

---

#### aiops-runner

职责:

```txt
Ansible 执行
SSH Runner 执行
Webhook Runner 执行
Kubernetes Runner 执行 (后续)
自动化日志流式推送
任务超时控制
执行状态更新
执行审计
执行后健康检查
```

`aiops-runner` 必须与 `aiops-server` 隔离。

Server 可以创建与审批任务, 但只有 Runner 才能执行。

---

### 3.3 模块四分类

`modules/` 下每个 Maven 模块必须明确属于下面四类之一。新增模块前先确认归类, 不要无限膨胀:

```txt
基础底座 (foundation):
  aiops-common
  aiops-persistence
  aiops-web
  aiops-security
  aiops-tenant
  aiops-user
  aiops-audit
  aiops-observability
  aiops-platform

运维领域 (operations-domain):
  aiops-datasource
  aiops-zabbix-adapter        # 外部系统适配器, 即使名字带 -adapter 也归入领域
  aiops-asset
  aiops-alert
  aiops-incident
  aiops-evidence
  aiops-rca
  aiops-report
  aiops-inspection
  aiops-runbook
  aiops-integration

执行体系 (execution):
  aiops-execution
  aiops-plugin
  apps/aiops-runner
  apps/aiops-worker           # 异步消费, 自身也是执行侧
  aiops-work-record           # 轻量记录, 与执行松耦合

AI 体系 (ai):
  aiops-ai-client             # Java 客户端
  apps/aiops-agent            # Python LangGraph 运行时
```

判断一个模块属于哪一类, 顺序:

```txt
1. 是否被 server/worker/runner 任一方复用?
2. 是否对外部系统做 IO?    -> 通常是 -adapter / -datasource / -ai-client
3. 是否承载 Incident 核心模型或证据链? -> operations-domain
4. 其它都按命名直观归类, 不要重复造平行的 "xxx-core" / "xxx-facade"。
```

详细包结构与依赖方向见 `references/module-package-conventions.md`。

---

### 3.4 依赖方向 (强制)

```txt
api        -> application
application -> domain
domain     -> (禁止任何 -adapter / -client / -web / -persistence)
infrastructure -> persistence + adapter + ai-client
```

跨模块调用必须经过 facade:

```txt
rca      -> EvidenceQueryService        (不允许直接 @Autowired EvidenceRepository)
report   -> IncidentReadService         (不允许直接查 incident 表)
agent    -> InternalAgentEvidenceApi    (不允许直接调 Zabbix / 数据库)
plugin   -> PlatformPluginRegistry      (不允许反射业务模块私有类)
```

禁止的反向依赖:

```txt
domain 依赖 -adapter / -client / -web / -persistence
server / worker / runner 互相直接调用 (允许通过 aiops-execution 的 ApplicationService)
runner 注入 ExecutionRepository / RollbackRepository (ArchUnit 守卫: RunnerArchUnitGuardTest)
领域模块把 spring-boot-starter-web 当成业务职责, 而非只用于 actuator endpoint
```

模块依赖若违反方向, 评审直接 fail, 修复方式: 新建 `service/*ApplicationService` facade, 让调用方只看到 DTO。

---

### 3.5 Spring 扫描范围

```txt
aiops-server      @SpringBootApplication(scanBasePackages = "io.aegisops")
aiops-worker      @SpringBootApplication(scanBasePackages = "io.aegisops")
aiops-runner      @SpringBootApplication(scanBasePackages = "io.aegisops")
```

后果: 只要模块里放了 Spring Bean, 它就会进入所有三 app 的容器。规则:

```txt
任何 RestController / @Configuration / @Service 必须明确归类到基础底座 / 领域模块的 api 包, 不要放进 domain 包
AI 诊断上下文相关的轻量 controller (如 InternalAgentEvidenceController) 放在 modules 下, 不放进 apps/aiops-server, 避免污染 server 主入口
Runner 内部执行器 (ansible / ssh / webhook) 全部放进 io.aegisops.runner.executor.* 包, 与 aiops-execution 的 dto/service 分开
```

---

## 4. 技术栈

### 4.1 前端

使用:

```txt
React 19
Vite
TypeScript
Tailwind CSS v4
TanStack Query
TanStack Table
ECharts
React Flow
Monaco Editor
SSE 用于流式
WebSocket 仅在 SSE 不够用时
```

组件栈分三层, 按需组合 (详见 `references/ai-agent-frontend-stack.md`):

```txt
后台壳子:        shadcn/ui (sidebar / dashboard / card / table)
AI Chat 基础:    shadcn/ui (message / bubble / message-scroller / attachment / marker)
Agent 工作台:    AI Elements (agent / tool / confirmation / task / plan / terminal / file-tree / stack-trace / test-results)
                    或 prompt-kit (chain-of-thought / reasoning / source / steps)
```

MVP 前端页面优先级:

```txt
1. 登录
2. 空 Dashboard
3. 数据源管理
4. 资产列表
5. 告警列表
6. Incident 列表
7. Incident 详情 (左侧时间线 + 中间 Agent 对话 + 右侧证据链)
8. Incident 时间线
9. AI 诊断面板
10. Runbook 列表
11. 自动化审批
12. 自动化日志
13. 审计日志
```

Incident 详情页布局强制使用三栏, 详见 `references/ai-agent-frontend-stack.md` §3。

规则:

```txt
不要在 React 页面里堆复杂的数据转换。
不要过度使用全局状态。
用 TanStack Query 管理服务端状态。
按路由组织页面, 按特性组织组件。
使用带类型的 API 客户端。
除非绝对必要, 不要使用 any。
shadcn/ui 组件优先于自己造组件; AI Elements / prompt-kit 在 shadcn 没有合适原语时再引入。
shadcn 组件不要手改 src/components/ui/ 下文件, 升级走 pnpm dlx shadcn@latest add --diff。
```

详见 `references/frontend-conventions.md` 与 `references/ai-agent-frontend-stack.md`。

---

### 4.2 后端

使用:

```txt
Java 21
Spring Boot 3.x 或 4.x
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

MVP 推荐后端技术栈:

```txt
Java 21
Spring Boot 3.x
Spring Security
JWT
MyBatis-Flex
PostgreSQL
Redis
ClickHouse
VictoriaMetrics
MinIO
Flyway
```

规则:

```txt
Controller 不允许包含业务逻辑。
Service 不允许直接调用外部系统。
外部系统访问必须通过 Adapter 或 Client。
主 API 响应禁止直接返回 raw Map<String, Object>。
DTO / VO / Entity / Domain 模型分层要清晰。
所有租户维度查询必须包含 tenantId。
所有安全敏感操作必须生成审计日志。
```

---

### 4.3 存储职责

按职责选择存储系统, 不要随机分配。

```txt
PostgreSQL:
  users
  tenants
  roles
  permissions
  datasources
  assets
  asset_relations
  alert 索引当前态
  incidents
  incident events
  diagnoses
  runbooks
  automation jobs
  audit logs
  deployment records
  knowledge metadata

Redis:
  缓存
  队列
  分布式锁
  限流
  临时 AI 上下文
  SSE 会话状态

VictoriaMetrics:
  主机指标
  服务指标
  API 指标
  资源指标
  时序数据

ClickHouse:
  原始告警事件
  Zabbix 事件明细
  日志
  RUM 事件
  时间线事件明细
  海量分析事件

MinIO:
  报告
  附件
  sourcemaps
  session replay 文件
  Ansible 产物
  上传的诊断文件

pgvector:
  MVP 向量检索:
    历史 Incident
    Runbook
    知识文档
    复盘报告

Milvus:
  仅在向量数据规模膨胀后才引入。
```

Phase 0 不要引入 Milvus, 除非显式要求。

Phase 0 不要引入 Kafka, 除非用户明确要求高吞吐接入。

---

## 5. 推荐仓库结构

目标结构:

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
├─ docs/
│  ├─ architecture.md
│  ├─ data-model.md
│  ├─ rca-design.md
│  ├─ ai-agent-design.md
│  ├─ automation-safety.md
│  └─ mvp-roadmap.md
│
└─ .agents/
   └─ aegisops/
      └─ SKILL.md
```

不要把所有后端代码压平到一个模块。

不要把 Zabbix、ClickHouse、VictoriaMetrics、AI Provider、自动化逻辑放到同一个 service 包内。

---

## 6. 核心领域模型

### 6.1 Tenant

所有业务对象必须是租户维度隔离, 除非显式声明为全局。

核心字段:

```txt
id
name
status
created_at
updated_at
```

---

### 6.2 User

核心字段:

```txt
id
tenant_id
username
display_name
email
password_hash
status
created_at
updated_at
```

严禁明文存储密码。

---

### 6.3 Role / Permission

使用 RBAC。

权限应支持以下资源维度:

```txt
datasource:read
datasource:write
asset:read
alert:read
incident:read
incident:write
incident:diagnose
runbook:read
runbook:write
automation:read
automation:approve
automation:execute
audit:read
admin:manage
```

---

### 6.4 DataSource

代表外部系统。

类型:

```txt
zabbix
victoriametrics
clickhouse
opentelemetry
prometheus
rum
webhook
github
gitlab
jenkins
```

核心字段:

```txt
id
tenant_id
type
name
endpoint
auth_type
encrypted_config
status
last_sync_at
created_at
updated_at
```

敏感信息必须加密, 或交由 Secret 存储管理。

不要在明文字段保存 token。

---

### 6.5 Asset

资产类型:

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

核心字段:

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

### 6.6 AssetRelation

关系类型:

```txt
depends_on
runs_on
contains
calls
owns
related_to
```

核心字段:

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

### 6.7 AlertEvent

统一告警事件。

来源:

```txt
zabbix
prometheus
rum
opentelemetry
webhook
manual
```

核心字段:

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

严重程度值:

```txt
info
low
warning
medium
high
critical
disaster
```

兼容外部输入 `average`，入库时归一为 `medium`；Zabbix 的 `disaster` 在接入映射层归一为
平台 `critical`，原始级别仍保留在 `raw_payload`。

状态值:

```txt
open
resolved
```

兼容外部输入 `recovered | closed | ok`，入库时统一归一为 `resolved`。

指纹规则:

```txt
source + asset_id + source_trigger_id + normalized_title
```

---

### 6.8 Incident

核心聚合根。

核心字段:

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

状态值:

```txt
open
investigating
mitigating
resolved
closed
ignored
```

规则:

```txt
一个 Incident 可包含多条 AlertEvent。
一个 Incident 可包含多条时间线事件。
一个 Incident 可包含多条 AI 诊断。
一个 Incident 可包含多个 AutomationJob。
一个 Incident 可生成一份 Postmortem。
```

---

### 6.9 IncidentEvent

用于关联 Incident 与事件。

事件类型:

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

关联类型:

```txt
primary
related
upstream
downstream
evidence
noise
```

---

### 6.10 IncidentTimeline

用于详情页展示。

时间线事件类型:

```txt
alert_triggered
metric_anomaly_detected
log_error_detected
change_detected
ai_diagnosis_created
runbook_recommended
automation_started
automation_finished
manual_note_added
incident_resolved
incident_closed
```

Incident 详情页应围绕时间线组织。

---

### 6.11 DiagnosisResult

AI 诊断必须是结构化的。

结构:

```json
{
  "summary": "string",
  "severity": "low | medium | high | critical",
  "suspectedRootCause": "string",
  "confidence": 0.0,
  "evidence": [
    {
      "type": "metric | log | alert | trace | change | zabbix | runbook | history",
      "description": "string",
      "sourceId": "string",
      "query": "string",
      "value": {}
    }
  ],
  "impact": {
    "affectedHosts": 0,
    "affectedServices": [],
    "affectedUsers": 0,
    "affectedTenants": []
  },
  "suggestions": [
    {
      "title": "string",
      "action": "string",
      "risk": "low | medium | high",
      "requiresApproval": true
    }
  ]
}
```

规则:

```txt
AI 输出必须解析与校验。
解析失败时返回安全的兜底结果。
AI 不得在提供上下文之外编造数据。
置信度必须显式给出。
证据必须引用实际采集到的数据。
```

---

### 6.12 Runbook

核心字段:

```txt
id
tenant_id
name
description
trigger_condition
risk_level
approval_required
enabled
created_at
updated_at
```

步骤类型:

```txt
manual
ssh
ansible
webhook
http
k8s
query_metric
query_log
health_check
```

---

### 6.13 AutomationJob

核心字段:

```txt
id
tenant_id
incident_id
runbook_id
status
risk_level
approval_required
created_by
approved_by
started_at
finished_at
input_json
output_json
created_at
updated_at
```

状态值:

```txt
pending
waiting_approval
approved
running
success
failed
cancelled
timeout
```

自动化日志:

```txt
id
job_id
step_id
log_time
level
content
```

---

### 6.14 AuditLog

所有敏感操作都必须生成审计日志。

必须包含:

```txt
id
tenant_id
actor_user_id
action
resource_type
resource_id
request_id
ip
user_agent
payload
created_at
```

审计必须覆盖:

```txt
login
logout
datasource create/update/delete
incident status change
AI diagnosis trigger
runbook create/update/delete
automation approval
automation execution
permission change
secret change
```

---

## 6.15 工作记录模块规则

工作记录是 AegisOps 的轻量可配置记录能力, 不是工单系统、流程引擎或低代码平台。

第一版只做最小闭环:

```txt
用户 / 角色复用
平台字典
管理员配置模板与字段
用户填写记录
列表筛选
受限导出
```

第一版不要做:

```txt
微服务
微前端
完整 LowCodeEngine
审批流
SLA
复杂统计
Excel 导入
评论
附件
告警联动
巡检联动
AI 总结
字段级权限
流程引擎
```

### 6.15.1 模块边界

后端工作记录必须放在:

```txt
modules/aiops-work-record
```

平台字典必须放在:

```txt
modules/aiops-platform/.../dictionary
```

不要把工作记录代码塞进:

```txt
aiops-incident
aiops-inspection
aiops-alert
aiops-platform 根目录
```

用户、角色、权限复用 `aiops-user` 与 `aiops-security`, 不重新实现一套用户系统。

### 6.15.2 前端边界

工作记录前端只放在 Portal:

```txt
web/portal/src/pages/work-records
web/portal/src/api/work-records
web/portal/src/components/work-records
web/portal/src/hooks/work-records
```

不要引入:

```txt
qiankun
module federation
iframe
独立子应用
```

工作记录 API 放在 `src/api/work-records`, 底层复用公共 `apiClient`; 页面和组件不得直接调用 axios。

平台字典放在 `src/api/dictionaries.ts` 与 `src/hooks/dictionaries`, 不要让平台页依赖 work-record 私有 API 文件。

### 6.15.3 数据库边界

第一版使用同一个 PostgreSQL, 工作记录使用独立 schema:

```sql
create schema if not exists work_record;
```

工作记录表使用:

```txt
work_record.wr_template
work_record.wr_template_field
work_record.wr_record
```

平台字典表使用:

```txt
platform_dict_type
platform_dict_item
```

不要第一版就多数据库、多 DataSource 或多事务管理。

命名规则:

```txt
工作记录表: wr_ 前缀
平台字典表: platform_dict_ 前缀
主键: uuid / varchar id, 与项目现有 ID 风格保持一致
租户字段: tenant_id
时间字段: created_at / updated_at / deleted_at
逻辑删除: deleted_at
启用禁用: enabled
```

Flyway migration 要拆清楚, 不要把建表、默认字典、默认模板、菜单种子全部塞进一个几百行 migration。已发布 migration 不得重命名或修改内容, 修复必须新增版本。

### 6.15.4 字典规则

通用枚举走平台字典, 临时枚举走字段 `options_json`。

第一批默认字典:

```txt
record_type
record_status
record_priority
env_type
yes_no
process_result
```

字典项不要物理删除, 只做 `enabled = false`, 因为历史记录可能仍引用旧 value。

记录保存时存 value, 不存 label:

```json
{
  "priority": "P2"
}
```

展示时再通过字典把 value 显示成 label。字典查询必须能支持展示历史值, 必要时包含已禁用项。

### 6.15.5 模板与字段规则

字段选项来源必须区分:

```txt
static: 字段自己维护 options_json
dict: 引用平台字典 dict_code
```

字段编码是动态表单稳定性的核心:

```txt
field_code 创建后不可随便修改
field_type 创建后尽量不可修改
字段删除只禁用, 不物理删除
```

必须拒绝以下保留 `field_code`:

```txt
id
tenant_id
template_id
title
status
owner_id
creator_id
record_time
created_at
updated_at
deleted_at
custom_data_json
builtin_data_json
```

自定义字段值类型必须统一:

```txt
text: string
textarea: string
number: number
date: YYYY-MM-DD
datetime: ISO string
select: string
multi_select: string[]
user: userId string
switch/boolean: boolean
```

第一版字段类型只做:

```txt
单行文本
多行文本
数字
日期
日期时间
单选
多选
人员
开关
```

先不要做:

```txt
级联选择
子表单
公式字段
联动显示
条件必填
复杂布局
远程接口字段
```

表单设计器第一版只解决添加字段、字段排序、属性编辑、禁用字段、保存模板与预览表单。不要把它做成页面级低代码平台。Formily / Designable 可作为后续复杂度上升后的选型评估, 不是第一版默认依赖; 完整 LowCodeEngine 第一版禁止引入。

### 6.15.6 记录数据规则

内置字段放主表:

```txt
title
status
owner_id
creator_id
record_time
created_at
updated_at
deleted_at
```

自定义字段放:

```txt
custom_data_json
```

不要把所有字段都塞进 JSON, 否则列表、权限、分页和排序都会变复杂。

创建记录时不要信任前端传:

```txt
tenant_id
creator_id
created_at
```

这些值必须由后端从登录上下文或服务端时间生成。

记录详情页必须按模板渲染历史记录:

```txt
读取 record.template_id
读取 template fields
按字段顺序渲染 custom_data_json
字典字段显示 label
禁用字段也要能显示历史值
```

### 6.15.7 查询、权限与导出

工作记录列表必须分页:

```txt
page 默认 1
size 默认 20
max size 100
```

普通用户只能查看自己的记录:

```txt
creator_id = 当前用户
or owner_id = 当前用户
```

记录管理员与系统管理员才能查看全部。权限必须由后端强制, 不允许只靠前端参数控制。

自定义字段筛选必须走白名单:

```txt
1. 根据 template_id 查询字段定义
2. 校验 field_code 存在
3. 校验 filterable = true
4. 根据 field_type 构造 JSONB 查询
```

禁止直接把前端传来的 `fieldCode` 拼进 SQL。

导出必须限制最大行数, 第一版建议最多 5000 或 10000 行。导出当前筛选结果即可, 后续再做异步导出。导出不能把登录 JWT 放进 URL。

### 6.15.8 后端校验、审计与测试

后端保存记录时必须校验:

```txt
必填字段
字段类型
数字格式
日期格式
单选值是否合法
多选值是否合法
字段是否属于当前模板
禁用字段不能写入
JSON 字段格式
```

所有模板、字段、字典、记录删除都优先禁用或软删除。涉及配置变更、记录删除、导出等敏感操作必须写审计日志。

动态字段相关测试至少覆盖:

```txt
字典 CRUD
禁用字典项后历史记录仍可展示
字段 required 校验
select 字段非法值拒绝
multi_select 字段非法值拒绝
filterable=false 字段不能筛选
普通用户不能查看别人记录
管理员可以查看全部记录
导出行数限制生效
```

强约束 ArchUnit 规则（必须满足，CI 守卫）:

```txt
workrecord domain 包不得 import org.springframework.web.* / springdoc / openapi
workrecord domain 包不得 import org.springframework.jdbc.* / org.jooq.* / javax.sql.DataSource
workrecord api 包不得 import org.springframework.jdbc.* / org.jooq.*
workrecord 任何类不得依赖 alert/incident/inspection 的 repository 包
dictionary 不能反向依赖 workrecord
```

具体包归属、命名后缀、ArchUnit 模板参见
`references/module-package-conventions.md` §7。每个强约束模块必须自带一个
`<Name>ArchUnitTest`（test scope，archunit-junit5），mvn verify 必须通过。

### 6.15.9 AI 实现纪律

AI 开发工作记录模块时必须小步推进, 不要一次实现完整系统。

推荐顺序:

```txt
1. 菜单 + 空页面
2. 字典表 + 字典 API + 字典页面
3. work-record 模块骨架
4. 模板表 + 字段表 + API
5. 表单设计器
6. 记录表 + 动态表单填写
7. 记录列表 + 筛选
8. 导出
```

每一步都必须有测试, 每次提交只做一个能力, 不要大范围重构现有系统。关键设计必须写进文档, 不要只存在聊天里。

---

## 7. Zabbix 集成规则

Zabbix 是数据源, 不是平台核心。

正确流程:

```txt
Zabbix Server
  ↓
Zabbix Adapter
  ↓
Asset / AlertEvent
  ↓
Incident 聚合
```

不要让 Ansible 直接调用 Zabbix。

不要让 AI 直接调用 Zabbix。

所有 Zabbix API 操作都必须在:

```txt
aiops-zabbix-adapter
```

中完成。

必备 Adapter 能力:

```txt
test connection
sync host groups
sync hosts
sync triggers
sync problems
sync events
host -> Asset 转换
problem/event -> AlertEvent 转换
保留 raw payload
归一 severity
生成 fingerprint
```

期望 Client 接口:

```java
public interface ZabbixClient {
    boolean testConnection();

    List<ZabbixHost> listHosts();

    List<ZabbixHostGroup> listHostGroups();

    List<ZabbixTrigger> listTriggers();

    List<ZabbixProblem> listProblems();

    List<ZabbixEvent> listEvents(ZabbixEventQuery query);
}
```

期望 Adapter 接口:

```java
public interface ZabbixAdapter {
    SyncResult syncAssets(Long tenantId, Long datasourceId);

    SyncResult syncAlertEvents(Long tenantId, Long datasourceId);
}
```

---

## 8. 指标集成规则

VictoriaMetrics 必须通过 Adapter 访问。

不要让 Controller 直接调用 VictoriaMetrics。

期望接口:

```java
public interface MetricQueryClient {
    MetricSeries queryRange(MetricRangeQuery query);

    InstantValue queryInstant(MetricInstantQuery query);
}
```

MVP 指标查询项:

```txt
CPU 使用率
内存使用率
磁盘使用率
网络 IO
主机可用性
触发器相关指标历史
```

Incident RCA 默认查询区间:

```txt
incident.started_at - 30 分钟
incident.started_at + 30 分钟
```

允许用户后续调整。

---

## 9. ClickHouse 集成规则

ClickHouse 存储高吞吐事件明细。

不要把 ClickHouse 作为主元数据库。

ClickHouse 用于:

```txt
原始告警事件 payload
Zabbix 事件明细
日志
时间线事件明细
RUM 事件 (后续)
```

规则:

```txt
只允许参数化查询。
禁止把用户输入拼接到 SQL。
大批量结果必须分页。
长时间查询必须设置超时。
```

---

## 10. AI Agent 规则

### 10.1 AI 职责

AI 可以:

```txt
汇总 Incident
解释证据
生成排障步骤
推荐 Runbook
匹配历史 Incident
生成 Postmortem 草稿
生成安全查询建议
解释风险
```

AI 不得直接:

```txt
执行 SSH
执行 Ansible
删除文件
回滚生产
修改配置
重启数据库
停止核心中间件
未经用户确认关闭 Incident
```

---

### 10.2 LLM Provider 抽象

使用 Provider 抽象。

必备接口:

```java
public interface LlmProvider {
    ChatResult chat(ChatRequest request);

    EmbeddingResult embed(EmbeddingRequest request);
}
```

可能实现:

```txt
OpenAiProvider
DeepSeekProvider
QwenProvider
OllamaProvider
```

不要在业务逻辑里硬编码 LLM 厂商。

---

### 10.3 AI 工具注册表

允许的工具:

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

禁止工具:

```txt
executeCommand
executeShell
restartServiceDirectly
deleteFileDirectly
modifyProductionConfigDirectly
```

执行必须走:

```txt
proposeAutomation
  ↓
AutomationJob
  ↓
approval
  ↓
aiops-runner
```

---

### 10.4 诊断 Prompt 规则

生成诊断时必须包含:

```txt
Incident 基础信息
相关告警
资产信息
指标上下文
时间线事件
RCA 规则证据
历史相似 Incident
可用 Runbook
自动化策略约束
```

AI 必须输出:

```txt
summary
impact
suspected root cause
confidence
evidence
recommended next steps
recommended runbooks
automation risk warning
```

证据不足时 AI 不得断言确定。

使用类似:

```txt
疑似
可能
根据当前证据
需要进一步确认
```

的语气, 当 confidence 偏低时。

---

## 11. RCA 引擎规则

MVP 使用:

```txt
规则式 RCA + 证据链 + AI 总结
```

MVP 不训练模型。

在基础 RCA 规则稳定之前, 不实现复杂图算法。

首批 RCA 规则:

```txt
R1: ±30 分钟内有变更
R2: 同一资产有多个告警
R3: 上游资产告警早于下游
R4: 问题集中在单主机 / 服务 / 版本
R5: 指标异常时间与日志错误重叠
R6: 存在历史相似 Incident
R7: 现有 Runbook 与当前 Incident 匹配
R8: 检测到告警风暴重复
```

每条 RCA 规则必须输出:

```txt
rule_id
score
evidence
related_asset_id
related_event_id
explanation
```

不得仅返回分数。

RCA 结果必须可解释。

---

## 12. 告警聚合规则

MVP 聚合规则:

```txt
同租户
同 fingerprint
相近时间窗口
同主资产
```

默认聚合窗口:

```txt
10 分钟
```

附加分组规则:

```txt
同主机 + 多种资源告警 -> 主机级 Incident
同主机组 + 大量告警 -> 主机组 Incident
同数据源 + 告警风暴 -> 风暴 Incident
```

不要过早过拟合。

聚合规则后续要可配置。

---

## 13. 自动化安全规则

自动化是危险的, 永远把安全放在第一位。

### 13.1 风险等级

低风险:

```txt
查日志
查指标
运行巡检脚本
创建工单
发送通知
读取进程列表
读取磁盘使用
读取服务状态
```

如果策略允许, 可免审批执行, 但必须留痕。

中风险:

```txt
重启无状态服务
清理临时文件
刷新缓存
调整副本数
```

默认需要审批。

高风险:

```txt
回滚发布
修改配置
流量切换
重启数据库
删除文件
```

必须审批 + 回滚预案 + 审计。

默认禁止:

```txt
rm -rf
drop database
truncate table
delete Kubernetes namespace
stop core middleware
modify firewall
flush Redis
format disk
```

未经用户明确需求与完整安全设计, 不得生成允许上述行为的代码。

---

### 13.2 执行流

所有自动化必须遵循:

```txt
AI 建议
  ↓
策略检查
  ↓
AutomationJob 创建 (status: pending)
  ↓
按需审批 (status: waiting_approval)
  ↓
Runner 接收审批通过的任务 (status: approved -> running)
  ↓
日志流回 server
  ↓
执行后健康检查
  ↓
更新任务状态 (success / failed / timeout)
  ↓
审计日志
  ↓
Incident 时间线追加 AutomationEvent
```

---

## 14. MVP Phase

### Phase 0: Foundation

目标:

```txt
项目骨架、基础设施、认证、数据库迁移。
```

交付物:

```txt
Maven 多模块工程
apps/aiops-server
apps/aiops-worker
apps/aiops-runner
React 控制台
Docker Compose
PostgreSQL
Redis
ClickHouse
VictoriaMetrics
MinIO
Flyway 迁移
Spring Security + JWT
基础 RBAC
OpenAPI
健康检查
```

验收:

```txt
docker compose 成功启动
server 成功启动
用户能登录
OpenAPI 可访问
租户/用户/角色可创建
空 Dashboard 渲染
```

Phase 0 基础未稳定前不实现 Zabbix。

---

### Phase 1: Zabbix 数据接入

目标:

```txt
使用 Zabbix 作为首个数据源。
```

交付物:

```txt
数据源管理页
Zabbix 数据源配置
test connection
同步主机组
同步主机
同步触发器
同步问题/事件
转换为 Asset
转换为 AlertEvent
资产列表
告警列表
```

验收:

```txt
配置 Zabbix 后, 主机与告警在 AegisOps 内可见。
Zabbix 问题转为 AlertEvent。
Raw payload 保留。
Fingerprint 生成。
```

---

### Phase 2: Incident Center

目标:

```txt
将告警列表升级为 Incident Center。
```

交付物:

```txt
AlertEvent 去重
Incident 创建
Incident 聚合
Incident 列表
Incident 详情
Incident 时间线
状态流转
严重度计算
```

验收:

```txt
重复 Zabbix 告警聚合到同一 Incident。
Incident 详情展示相关告警、资产与时间线。
```

---

### Phase 3: RCA 引擎

目标:

```txt
基于指标、资产、事件生成证据链。
```

交付物:

```txt
VictoriaMetrics Adapter
指标上下文查询
Incident 指标面板
RCA 规则引擎
证据模型
根因打分
RCA 结果展示
```

验收:

```txt
Incident 详情可展示事件窗口附近的指标。
RCA 引擎输出证据, 而不仅是文本。
```

---

### Phase 4: AI 诊断

目标:

```txt
使用 AI 把 RCA 证据汇总为可读诊断。
```

交付物:

```txt
LLM Provider 抽象
任一 Provider 实现
AI 工具注册表
诊断 Prompt
结构化 DiagnosisResult
SSE 流式
诊断持久化
AI 诊断面板
```

验收:

```txt
点击 AI Diagnose 生成包含 summary、suspected root cause、evidence、confidence、suggestions 的报告。
```

---

### Phase 5: Runbook 与 Ansible

目标:

```txt
从诊断到受控执行。
```

交付物:

```txt
Runbook 模型
Runbook 步骤
Ansible Runner
AutomationJob 模型
审批流
执行日志
结果回写
Incident 时间线集成
```

内置 Runbook:

```txt
主机巡检
磁盘检查
Nginx 状态检查
Java 进程检查
服务重启 (需审批)
```

验收:

```txt
Incident 可推荐 Runbook。
用户可审批执行。
Runner 执行巡检 Playbook。
日志流回。
结果写入 Incident 时间线。
```

---

### Phase 6: Postmortem 与知识库

目标:

```txt
每个 Incident 都沉淀为可复用知识。
```

交付物:

```txt
Postmortem 草稿
历史 Incident 库
Runbook 与 Incident 关联
pgvector 检索
相似 Incident 检索
AI 诊断基于历史增强
```

验收:

```txt
关闭 Incident 时生成 Postmortem 草稿。
下一个相似 Incident 可引用历史。
```

---

### Phase 7: AI Agent 工作流加固

目标:

```txt
在不绕过 Runner / 审批安全的前提下加固 AI 诊断工作流。
```

交付物:

```txt
LangGraph 工作流模块化
多 Agent 协作 (证据 / RCA / Runbook 审查)
Agent 记忆与 checkpoint 支持
Agent 评测数据集与 Prompt Profile
内部工具策略校验
Agent 运行可观测性
```

验收:

```txt
AI 诊断保持证据驱动。
AI 工具只能通过内部受控 API 取证据与历史案例。
AI 输出能推荐 Runbook 但不能直接执行自动化。
Agent 运行可追溯、可评测、按租户隔离。
```

---

### Phase 8: 生产加固

目标:

```txt
为 MVP 的 SaaS 多租户隔离、插件策略、私有化部署做准备。
```

交付物:

```txt
SaaS 多租户加固
插件描述符与租户插件策略
私有化部署清单
离线包流程
生产安全检查清单
CI 与本地质量门禁
```

验收:

```txt
API、持久化、内部 Agent 工具、插件策略都守护租户边界。
可构建并验证私有化部署产物。
本地与 CI 环境下质量门禁一致运行。
```

---

### Phase Z9: Zabbix MVP 端到端验收

目标:

```txt
冻结一次面向完整 Zabbix Incident 闭环的演示验收场景。
```

范围:

```txt
Phase Z9 是一个场景关卡, 不是产品 Phase。
可以使用 mock 故障注入验证 MVP 路径。
不得借此重定义生产拓扑或绕过 Runner / 审批安全。
```

验收:

```txt
故障注入触发 Zabbix。
Zabbix 将告警送入 AegisOps。
AegisOps 创建 AlertEvent 与 Incident。
证据、RCA、AI 诊断、Runbook 推荐、执行结果、Postmortem 均可见。
```

---

## 15. MVP 不要做的事

除非显式要求, 不要做以下事情:

```txt
完整替代 Prometheus
完整日志平台
完整 Tracing 平台
复杂 Kubernetes Operator
多区域 HA
复杂 CMDB
巨型大屏
完全自治自愈
自训练模型
完整 Milvus 部署
完整 Kafka 流水线
过早微服务
复杂 License 系统
```

避免平台臃肿。

---

## 16. API 设计规则

优先 REST。

AI 输出与 Runner 日志的流式场景使用 SSE。

只有在双向实时通信必要时才使用 WebSocket。

基础 API 分组:

```txt
/api/auth
/api/users
/api/roles
/api/tenants
/api/datasources
/api/assets
/api/alerts
/api/incidents
/api/ai
/api/runbooks
/api/automation
/api/audit
```

必备端点:

```txt
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me

GET  /api/datasources
POST /api/datasources
POST /api/datasources/{id}/test
POST /api/datasources/{id}/sync

GET  /api/assets
GET  /api/assets/{id}
GET  /api/assets/{id}/relations

GET  /api/alerts
GET  /api/alerts/{id}
POST /api/alerts/{id}/ignore

GET  /api/incidents
POST /api/incidents
GET  /api/incidents/{id}
GET  /api/incidents/{id}/timeline
POST /api/incidents/{id}/diagnose
POST /api/incidents/{id}/resolve
POST /api/incidents/{id}/close

GET  /api/runbooks
POST /api/runbooks
GET  /api/runbooks/{id}
POST /api/runbooks/{id}/execute

GET  /api/automation/jobs
POST /api/automation/jobs
POST /api/automation/jobs/{id}/approve
POST /api/automation/jobs/{id}/cancel
GET  /api/automation/jobs/{id}/logs

GET  /api/audit/logs
```

---

## 17. 数据库迁移规则

使用 Flyway。

规则:

```txt
任何 Schema 变更都必须新增 migration。
不要修改已经合并的旧 migration。
适用处使用 tenant_id。
为租户维度查询创建索引。
为状态与时间范围查询创建索引。
元数据类灵活字段使用 JSONB。
敏感信息不得明文存储。
```

命名:

```txt
V0001__init_tenant_user_rbac.sql
V0002__init_datasource_asset.sql
V0003__init_alert_incident.sql
V0004__init_ai_runbook_automation.sql
V0005__init_audit.sql
```

---

## 18. 测试规则

每个 Phase 必须包含测试。

后端测试:

```txt
领域逻辑单元测试
聚合 Service 测试
外部系统 Adapter 测试 (mock)
Controller API 契约测试
迁移校验
```

前端测试:

```txt
组件冒烟测试
API Mock 测试
关键页面渲染测试
Playwright E2E (后续)
```

高优后端测试:

```txt
告警指纹生成
AlertEvent 去重
告警 -> Incident 聚合
Incident 状态流转
RCA 规则打分
DiagnosisResult 解析
AutomationJob 状态机
RBAC 权限校验
审计日志生成
```

未真正运行相关测试时, 不要声称完成。

---

## 19. 评审清单

代码评审时检查:

```txt
是否保持 Incident 为核心模型?
是否遵循当前 MVP Phase?
是否避免过早微服务?
是否通过 Adapter 访问外部系统?
是否守护租户隔离?
是否避免 AI 直接执行?
是否对高危动作设置审批?
是否生成审计日志?
是否包含测试?
是否更新 migration?
API 变更是否同步 OpenAPI 与 docs?
是否避免过度设计?
新增模块是否归入基础底座 / 运维领域 / 执行体系 / AI 体系 四类之一?
跨模块调用是否经过 facade (service/*ApplicationService), 而非直接注入 Repository?
端口是否与 references/architecture-boundaries.md §1 端口表冲突?
```

### 19.1 Git 分支与 PR 规则

功能开发、Bug 修复、重构、CI 或文档等仓库变更必须遵循:

```txt
1. 修改文件前, 从最新 mvp 创建独立分支或 worktree。
2. 禁止直接在 mvp 分支修改、提交或推送。
3. 一个任务使用一个分支或 worktree, 不复用已合并或已关闭 PR 的分支, 不混入无关变更。
4. 完成影响面验证后提交并推送任务分支。
5. 创建目标分支为 mvp 的 PR, 通过 PR 合并变更。
```

---

## 20. Agent 响应风格

提出实现方案时使用以下结构:

```txt
1. 范围
2. 需新增/修改的文件
3. 数据模型变更
4. 后端变更
5. 前端变更
6. 测试
7. 验证命令
8. 风险 / 后续
```

给出代码时, 优先提供完整文件内容或精确补丁。

未实现的功能请明确说明。

未真正完成验证前, 不要说“完成”。

---

## 21. Phase 纪律

始终先识别当前 Phase。

如果用户要求 Phase 0, 不要实现 Phase 3 的 AI 诊断。

如果用户要求 Phase 1, 不要实现 Ansible Runner。

如果用户要求 Phase 5, 复核 Phase 0–4 的前提是否仍成立。

默认 Phase 顺序:

```txt
Phase 0: foundation
Phase 1: Zabbix ingestion
Phase 2: Incident center
Phase 3: RCA engine
Phase 4: AI diagnosis
Phase 5: Runbook and Ansible
Phase 6: postmortem and knowledge
Phase 7: AI Agent workflow hardening
Phase 8: production hardening
Phase Z9: Zabbix MVP 端到端验收场景 (非产品 Phase)
```

---

## 22. 默认实现优先级

优先:

```txt
简单的领域模型
清晰的 Adapter 边界
可解释的 RCA
安全的自动化
可测试的 Service
带类型的 API 响应
迁移优先的数据库变更
```

避免:

```txt
数据正确性之前先做大屏
证据模型之前先堆 AI Prompt 复杂度
审批流之前先做自动化
资产基础之前先做图拓扑
MVP 之前先拆微服务
Redis Stream 不够之前先上 Kafka
pgvector 不够之前先上 Milvus
```

---

## 23. 最终 MVP 演示要求

成功的 MVP 必须能够演示:

```txt
1. 用户登录。
2. 用户新增 Zabbix 数据源。
3. 平台同步主机与告警。
4. Zabbix 触发 CPU 告警。
5. 平台创建 AlertEvent。
6. 平台聚合 Incident。
7. Incident 详情展示相关资产、告警、时间线与指标。
8. 用户点击 AI Diagnose。
9. AI 输出证据驱动的诊断。
10. 系统推荐巡检 Runbook。
11. 用户审批执行。
12. aiops-runner 执行 Ansible 巡检。
13. 日志流回。
14. 结果写入 Incident 时间线。
15. 用户关闭 Incident。
16. 平台生成 Postmortem 草稿。
```

实现不支持这条路径, 就不是 MVP-complete。

---

## 24. 最强提醒

产品价值不是:

```txt
更多大屏
更多图表
更多数据源
更多 AI 聊天
更多微服务
```

产品价值是:

```txt
把原始告警变成可解释的 Incident,
把 Incident 变成安全的动作,
把动作变成可复用的知识。
```

---

## 25. 文档治理

所有项目文档必须遵循:

```txt
.agents/skills/aegisops/references/doc-governance.md
```

关键规则:

```txt
- 所有文档放入 docs/
- 每篇文档必须有 YAML frontmatter (title, type, status, phase, owner, created, updated, related)
- 流程文档 (design, review, fix) 按 Phase 分目录: docs/designs/<phase>/, docs/reviews/<phase>/, docs/fixes/<phase>/
- ADR 使用编号文件名: docs/adr/NNNN-slug.md
- 流程文档使用日期前缀: YYYY-MM-DD-slug.md
- 接受的 ADR 一律不得修改; 若要变更决策, 新建一份
- docs/INDEX.md 自动生成, 不允许手工编辑
```

使用 docs 脚本创建和管理文档:

```txt
npx tsx scripts/docs.ts init    # 初始化目录结构
npx tsx scripts/docs.ts new <type> <slug> --title "Title" --phase phase-0
npx tsx scripts/docs.ts check    # 校验 frontmatter 与命名
npx tsx scripts/docs.ts index     # 重新生成 docs/INDEX.md
```

Agent 参考文档:

```txt
.agents/skills/aegisops/references/doc-governance.md           # 文档规则
.agents/skills/aegisops/references/phase-checklist.md          # Phase 启动/收尾清单
.agents/skills/aegisops/references/architecture-boundaries.md  # 模块与应用边界, 端口表
.agents/skills/aegisops/references/module-package-conventions.md # 业务模块的 domain/application/infrastructure/api 包结构
.agents/skills/aegisops/references/automation-safety.md        # 风险等级与安全规则
.agents/skills/aegisops/references/frontend-conventions.md     # web/console 主题、组件、样式与代码组织规范
.agents/skills/aegisops/references/ai-agent-frontend-stack.md  # shadcn/ui + AI Elements + prompt-kit 选型与 AI 页面骨架
```
