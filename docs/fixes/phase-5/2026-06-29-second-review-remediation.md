---
title: 第二轮回盘问题修复总记录
type: fix
status: in-progress
phase: phase-5
owner: ai
created: 2026-06-29
updated: 2026-06-30
related:
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/aegisops/references/architecture-boundaries.md
  - .agents/skills/aegisops/references/doc-governance.md
  - AGENTS.md
---

# 第二轮回盘问题修复总记录

## 0. 背景

2026-06-29 对 AegisOps 仓库做第二轮回盘，发现 12 类问题横跨文档、架构、模块边界、调度链、Flyway 命名、前端缺失。本文件一次性记录所有问题的事实、影响、推荐修复方案，作为后续 10 个 PR 的总施工蓝图。

`AGENTS.md` 与 `.agents/skills/aegisops/SKILL.md` 在本轮修复前存在**部分内容冲突**。本轮由 Owner 决断（2026-06-29）：**以 SKILL.md + references/\*.md 为单一真相源，AGENTS.md 不与 SKILL.md 任一章节重复**。决断已落地为 PR-mega-1（AGENTS.md 全文重写 1733 → 441 行，详见 §1）。

## 1. 文档源头决断（已落地 PR-mega-1）

### 事实（修复前）

- `AGENTS.md` 1733 行，包含 17 章 commit 规范、21 章工程纪律
- `.agents/skills/aegisops/SKILL.md` 1548 行，包含产品定义、Phase 0~6 路线图、Flyway 命名规范、领域模型
- `.agents/skills/aegisops/references/*.md` 5 份（architecture-boundaries、automation-safety、doc-governance、phase-checklist、skill-index）
- 多份文档**部分冲突**：
  - AGENTS §21.2 跨模块必须经 Application Service，SKILL 无此条
  - AGENTS §17.3.1 commit 示例是 `phase2_incident_aggregation.sql`，SKILL §17 命名规范是 `V0001__init_tenant_user_rbac.sql`
  - AGENTS §17.3 明文「涉及数据库变更必须显式写《迁移:》（MVP 阶段不强制版本号）」，与 SKILL §17 强制版本号机制冲突
  - AGENTS §21.8 禁 v1/legacy/old/previous 命名，SKILL 无此条
  - AGENTS §1 产品定位、§3 技术栈、§4 仓库结构、§5 应用职责、§6 领域模型、§7 RCA、§8 AI Agent、§9 自动化安全、§10 API、§11 Roadmap、§12 不做内容、§13 后续版本、§14 编码规范、§15 测试要求——15 个章节全部与 SKILL.md 重复

### 方案比选（已决）

| 方案                                                        | 代价                                                                                                   | 决策   |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ | ------ |
| A. 删除 AGENTS.md，只留 SKILL.md                            | 违反 doc-governance.md 「AGENTS.md 在根目录保留」豁免；丢 commit 规范和工程纪律这两份 SKILL 没有的内容 | 否     |
| B. 删除 SKILL.md，只留 AGENTS.md                            | 文档治理与产品定义全失                                                                                 | 否     |
| C. **AGENTS.md 瘦身到只留独有内容，其他章节 cite SKILL.md** | 必须做字符串级证据链核查，否则会有死链                                                                 | **是** |
| D. 双文件原样保留，新增对照表                               | 搁置冲突而不是解决                                                                                     | 否     |

**采纳方案 C。**

### 修复方案（已落地 PR-mega-1）

`AGENTS.md` 全文重写，1733 → 441 行（压缩 74%，但**零信息损失**——所有被删章节在 SKILL.md 有等价内容）：

```txt
删除与 SKILL 重复的章节（15 个）:
  §1  产品定位        → SKILL.md §1
  §3  技术栈          → SKILL.md §4
  §4  仓库结构        → SKILL.md §5
  §5  应用职责        → SKILL.md §3.2
  §6  核心领域模型    → SKILL.md §6
  §7  RCA 设计原则    → SKILL.md §11
  §8  AI Agent 原则   → SKILL.md §10
  §9  自动化安全原则  → SKILL.md §13
  §10 API 设计约定    → SKILL.md §16
  §11 MVP Roadmap    → SKILL.md §14
  §12 不做的内容     → SKILL.md §15
  §13 后续版本方向   → SKILL.md V0.2~V0.5
  §14 编码规范       → SKILL.md §4.2 / §19
  §15 测试要求       → SKILL.md §18

保留并修订的章节（4 个）:
  §1  Agent 工作方式          保留并引用 SKILL.md §21
  §2  Git 提交与变更规范       保留（AGENTS 独有硬约束）
  §3  工程纪律与代码质量       保留并重排为 3.1~3.10（原 21.1~21.10）
  §4  最高优先级提醒           保留并标注与 SKILL §24 同源

新增（1 个）:
  §0  文档源头与覆盖关系       写在最顶部，明文优先级 + 仲裁顺序
```

具体落地变更点：

```txt
- 顶部新增 §0 「文档源头与覆盖关系」:
    优先级 SKILL.md > references/architecture-boundaries > references/automation-safety
            > references/doc-governance > references/phase-checklist > AGENTS.md
    内容冲突时以 SKILL + references 为准；AGENTS.md 新增章节不得与 SKILL 任何 §X 重复
    进入 V0.2 后由 Owner 评审合并

- §2.2.3 commit 示例: phase2_incident_aggregation.sql → V0006__init_alert_incident_extend.sql
- §2.3 正文: 删「MVP 阶段不强制版本号」一句,改为「涉及数据库变更必须显式写《迁移:》并指明 Flyway 版本号（按 SKILL.md §17 命名）」
- §2.3.1 commit 示例: phase2_incident_aggregation.sql → V0006__init_incident_aggregation.sql
- §2.7 自检清单: 同上
- §3.9 自检关键字: 5 项 → 8 项精炼
    原:  @Deprecated / legacy / v1 / old / previous
    新:  @Deprecated / _legacy / legacy_ / _old / old_ / _previous / previous_ / v1_ / _v1
  并新增豁免说明: Flyway 迁移版本号遵循 SKILL §17 命名 V\d{4}__init_[a-z_]+\.sql,不应被当作 v1 误报
- §3.1~§3.8: 原 §21.1~§21.7 内容保留,编号重排 3.1~3.8
- §3.10 自检清单: 引用 §3.1~§3.9（不再引用旧的 §21.x）
```

### 状态

- **PR-mega-1 已完成**（AGENTS.md 全文重写 1733 → 441 行）
- **未提交 git**：本修改在本地工作区，按 AGENTS §1 第 14 条需走 PR 流程
- 建议 commit 主题：`docs(governance): 与 SKILL.md 合并文档源头约定`
- 建议分支：`docs/governance/merge-conventions-with-skill`
- Owner 审阅通过后才能 push

---

## 2. worker 空壳（P0）

### 事实

- `apps/aiops-worker/` 仅含 `AiOpsWorkerApplication` + `WorkerController`（17 行，返回 `phase=phase0`）
- 全仓没有任何 `aiops.worker.*` 配置、没有 `WorkerClient`
- `ExecutionRequestService`、`PostmortemService`、`WebhookConnectorService`、`AnsibleResourceService`、`IncidentCaseService`、`AgentMemoryService`、`KnowledgeBaseIndexService` 等约 15 个 Service 全部挂载在 `aiops-server` 进程
- 后果：worker app 仅是形式上的「三 app」占位，违反 SKILL §3.2 三 app 职责、违反 architecture-boundaries 依赖规则

### 影响

- 架构文档说一套，代码做另一套
- Zabbix 同步、RCA、Postmortem、AI 诊断都由 server 同步调用 worker 应承担的功能
- runner 自己造轮子轮询 `execution_run` 表，绕开 worker 是为了应对 worker 缺位
- 后续 Phase 4 ~ 6 工作（Agent Graph、Knowledge Base、Memory、Postmortem）会在 server 内继续膨胀，违反 §3.1 模块化单体优先原则

### 修复方案（方案 A · 骨架优先）

```txt
1. 在 worker 进程新增 RuntimeTopology bean，声明本进程绑定的 @Scheduled 任务清单
2. 新增 4 个 Job 接口 + 骨架实现：
   - ZabbixSyncJob         (Phase 1 实装)
   - IncidentAggregationJob(Phase 2 实装)
   - RcaAndDiagnosisJob    (Phase 3/4 实装)
   - PostmortemDraftJob    (Phase 6 实装)
3. 新增 OutboxPoller 任务，填补 server → worker 派单缺口：
   - SELECT FROM automation_outbox WHERE status='pending' AND target_app='worker'
   - 按 outbox.payload 路由到对应 Job
   - 写 status='done' / 'failed'，失败重试 3 次
4. server 现有 Zabbix/Incident/RCA/AI/Postmortem 触发点改为「写 outbox，不直接执行」
5. Flyway 新增 V0006__init_automation_outbox.sql（按 SKILL §17 命名）
6. 集成测试：模拟 Zabbix event → server 落 outbox → worker pick → 更新 DB
```

### 状态

- **PR5 已完成（L3 schema + L3 调度链重构）**
  - Flyway 新增 `V0006__init_automation_outbox.sql`（`automation_outbox` 表 + 2 个索引），
    与 PR3 拆分风格一致（按 SKILL §17 命名）。
  - worker 新增 `OutboxProperties` + `JooqOutboxRepository` + `OutboxPoller`（事务化 claim → 路由 → 写回 done / failed / 重试）
    - `WorkerRuntimeTopology`（固定周期 TaskScheduler + 首启动立即 tick）。
  - worker 新增 4 个 Job 接口骨架：`ZabbixSyncJob` / `IncidentAggregationJob` / `RcaAndDiagnosisJob` /
    `PostmortemDraftJob`，按 Phase 1/2/3/4/6 顺序实装。
  - `apps/aiops-common.outbox.OutboxWriter` 作为跨 app 派单的统一入口（REQUIRED 事务传播，
    与调用方事务同生共死，零孤儿行）。
  - server 侧 `IncidentPostmortemDispatcher` 提供 `dispatchIncidentAggregation` /
    `dispatchRcaDiagnosis` / `dispatchPostmortemDraft` 三个派单方法。
  - worker 单测：`OutboxPropertiesTest`（7）+ `OutboxPollerTest`（6），全绿。

---

## 3. 三 app 调度链断裂（P0）

### 事实

- server 直接写 `execution_run` 表 + `automation_plan` 表 + `ai_diagnosis` 表
- runner 通过 `RunnerScheduler` 轮询 `execution_run`，拿到任务后执行 Ansible
- worker 进程不参与派单
- 整条派单链是「server → execution_run → runner」，绕开了 worker 这一环

### 影响

- architecture-boundaries.md 第 110 行明文写「No reverse dependencies. Server never imports runner code.」，但实际 server 通过数据库与 runner 共享状态（这是 OK 的，runner 主动轮询）
- 真正的问题：worker 应是「分析层」，但目前分析层职责被 server 同步承担，runner 收到执行任务时，RCA/AI 阶段产物可能尚未生成

### 修复方案

与第 2 节 worker 修复**绑定**。引入 outbox 表后，调度链变为：

```txt
Zabbix webhook
  → server 接收并落 alert_event 表 + 写 outbox(target_app='worker', job='zabbix-sync')
  → worker OutboxPoller pick
  → worker ZabbixSyncJob 落 asset / alert_event / incident / timeline
  → worker 写 outbox(target_app='server', job='trigger-rca-diagnosis')
  → server 接收并落 automation_plan + execution_run
  → runner RunnerScheduler pick
  → runner 执行 Ansible / Webhook
  → runner 写 outbox(target_app='worker', job='postmortem-draft')
  → worker PostmortemDraftJob pick 生成复盘草稿
```

### 状态

- **PR5 已完成（与 §2 同 PR，L3 调度链重构）**
  - `ZabbixWebhookService.ingest` 写入 alert_event 后立即 `OutboxWriter.enqueue('worker', 'zabbix-sync', payload)`。
  - `IncidentPostmortemDispatcher` 暴露 3 个 dispatch 方法，由后续 server controller 直接调用。
  - 端到端路径落地：Zabbix webhook → server → outbox(target_app='worker') → worker `OutboxPoller` →
    `ZabbixSyncJob` → 后续 incident / RCA / postmortem 走同一 outbox 通道。
  - 集成测试：`mvn test` 全 reactor 通过（apps/aiops-server 10/9+1 skipped、aiops-worker 16/16、
    aiops-runner 59/59、aiops-integration 14/14）；PhaseZ9ZabbixMvpFlowTest 1 skipped 为
    环境依赖（Zabbix sandbox），与本次改动无关。
  - 后续 runner → worker（postmortem-draft）与 worker → server（trigger-rca-diagnosis）由 PR5
    预留接口（`OutboxWriter` + `JobResult` 契约），留待对应 Phase 实装时接通。

---

## 4. health 端点 phase 标签错位（P0）

### 事实

```java
// apps/aiops-worker/src/main/java/io/aegisops/worker/WorkerController.java
@GetMapping("/health")
public Map<String, Object> health() {
  return Map.of("status", "idle", "phase", "phase0");
}
```

- runner 已经在 Phase 5（Ansible 完整链路），但 `RunnerController` 也返回 `phase=phase0`
- 这种 hardcode 会让监控、PR 评审、Owner 验收时误判

### 修复方案

```txt
1. 新增 aiops-common RuntimePhase 枚举：
   PHASE_0, PHASE_1, PHASE_2, PHASE_3, PHASE_4, PHASE_5, PHASE_6
2. 新增 aiops.runtime.phase 配置项，默认 phase=phase0
3. 各 app application.yml 显式声明：
   aiops-server  → aiops.runtime.phase=phase5
   aiops-worker  → aiops.runtime.phase=phase5
   aiops-runner  → aiops.runtime.phase=phase5
4. 两个 health controller 改为读取 @ConfigurationProperties("aiops.runtime")，返回真实 phase
5. 后续 @ConditionalOnProperty(prefix="aiops.runtime",name="phase")
   守门「不在当前 phase 的 Service 不被注册」，落 AGENTS §21 Phase Discipline
```

### 状态

- **PR2 已部分完成（commits `acf856e` + `02896d6`）**
  - 1: `RuntimePhase` 枚举 + `RuntimeProperties` 已在 `acf856e` 落地（aiops-common）
  - 2: `aiops.runtime.phase` 配置项已在 3 个 app yml 显式声明 `phase=phase5`
  - 3: `WorkerController#status` / `RunnerController#status` 已读取 `RuntimeProperties`
  - 4: `SystemController#status` 已新增（commit `02896d6`），与 worker/runner 对齐
  - 5: `@PhaseEnabled(value = PHASE_5)` + `PhaseEnabledCondition` 已在 `aiops-common`
    落地（`02896d6` 后续 commit，PR-Tier L2），并通过
    `PhaseEnabledConditionTest`（17 个 case）+ `PhaseGateIntegrationTest`
    （5 个 case）守门全相位。后续 phase 接入的服务（典型为 Phase 5 的
    `AnsibleRunnerService`、Phase 4 的 AI diagnosis、Phase 6 的 postmortem）
    只需在类上挂 `@PhaseEnabled(RuntimePhase.PHASE_X)` 即可纳入 Phase Discipline。

---

## 5. Flyway 命名规范不符（P1）

### 事实

- 当前文件：`apps/aiops-server/src/main/resources/db/migration/V1__phase_final_schema.sql`（~1500 行）
- SKILL §17 命名规范：`V0001__init_tenant_user_rbac.sql`、`V0002__init_datasource_asset.sql`、`V0003__init_alert_incident.sql`、`V0004__init_ai_runbook_automation.sql`、`V0005__init_audit.sql`

### 影响

- 一个文件 1500 行，所有 schema 改动都加在这个文件尾部，永远退不出来
- 文件名带 `phase_final` 是临时标签，与 SKILL 命名不一致
- AGENTS §21.8 有人会误读为「不分版本」，实际 Flyway 机制本身就需要版本号

### 修复方案（clean slate，Owner 已批准）

```txt
1. 把 V1__phase_final_schema.sql 一次性拆成 5 个迁移：
   V0001__init_tenant_user_rbac.sql
   V0002__init_datasource_asset.sql
   V0003__init_alert_incident.sql
   V0004__init_ai_runbook_automation.sql
   V0005__init_audit.sql
2. 删除 V1__phase_final_schema.sql
3. 新增 MigrationOrderTest：
   - 扫描 db/migration
   - 断言所有文件名匹配 ^V\d{4}__init_[a-z_]+\.sql$
   - 断言 schema_history 表当前已执行记录 ≤ 文件数
4. 启动路径验证：docker compose down -v && mvn spring-boot:run 跑通
5. 同步 AGENTS.md §17.3.1 commit 示例文本（如有 phase2_xxx 等过时内容）
```

### 状态

- **PR3 已完成（commit `4a36a3c`）**
  - `V1__phase_final_schema.sql`（1717 行）已拆分为 5 个 V0001..V0005
    `init_*.sql`（70 CREATE TABLE 全数保留，无遗漏/重复）。
  - 删除旧 V1 文件。
  - `FlywayMigrationVersionUniquenessTest`（`4a36a3c`）已扩展为 3 个测试方法：
    命名正则匹配 `^V\d{4}__init_[a-z_]+\.sql$`、版本号唯一性、严格单调递增。
  - 端到端：`mvn test` 全 reactor 通过，jOOQ 重新生成 70 个表元模型无报错。

---

## 6. runner 跨模块直接注 Repository（P1）

### 事实

- `apps/aiops-runner/.../RunnerExecutionService` 注入 `io.aegisops.execution.ExecutionRepository`、`RollbackRepository` 等（来自 `modules/aiops-execution`）
- AGENTS §21.2 写「跨模块调用必须经过 Application Service，禁止 Module A 直接注入 Module B 的 Repository」
- SKILL / architecture-boundaries 没明文禁，但 architecture-boundaries §123 写「No reverse dependencies. Server never imports runner code.」

### 影响

- runner 直接持有 aiops-execution 的 Repository 实体，破坏模块边界
- 单元测试需要 mock Repository，无法用 ApplicationServiceFake 替换

### 修复方案

```txt
1. 在 modules/aiops-execution 下新增 ExecutionApplicationService：
   - public Optional<ExecutionRunRecord> claimNext(...)
   - public void heartbeat(...)
   - public void updateStep(...)
   - public void appendArtifact(...)
2. 同包下新增 RunnerInboundService（仅 runner 可见）
3. RunnerExecutionService 改为只注入 ExecutionApplicationService + RollbackApplicationService
4. 引入 ArchUnit 测试守门：
   - runner 进程的 source set 禁止 import io.aegisops.execution.*Repository
   - 允许 import io.aegisops.execution.service.* / contract.*
5. 现有 RunnerExecutionServiceTest 改用 ApplicationServiceFake 替换 RepositoryFake
```

### 状态

- **PR4 已完成（L2 refactor + ArchUnit 守门）**

---

## 7. apps/demo-order-service 不在三 app 列表里（P1）

### 事实

- `apps/` 下有 `aiops-server`、`aiops-worker`、`aiops-runner`、`demo-order-service`
- SKILL §3.2 与 AGENTS §2.3 明确「MVP 只允许三个后端应用」
- `demo-order-service` 是 Phase Z9 demo 用的 order 模拟数据源，**没有 ADR 说明为什么需要第四个 app**

### 修复方案

```txt
1. 写 ADR 0002-mvp-fourth-app-justification.md：
   - 解释 demo-order-service 用途
   - 明确 dev/staging/prod profile 下的可见性
2. 短期：application.yml 加 spring.profiles 配置
   - 默认 prod 不启用 demo-order
   - dev/staging 显式 --spring.profiles.active=demo
3. CI 流水线：
   - mvn verify 跑 prod profile（不含 demo）
   - mvn verify -Pdemo 跑 dev profile（含 demo）
4. 长期：考虑将 demo-order 内联到 integration test 的 docker-compose.override.yml
```

### 状态

- **PR6 已完成（ADR + Spring profile 守门 + Maven profile 划分）**
  - ADR 0002（`docs/adr/0002-mvp-fourth-app-justification.md`）正式接受 demo-order-service
    作为 Phase Z9 demo 期间的辅助应用，明确其**不进入 MVP 生产运行时拓扑**
  - `apps/demo-order-service/.../DemoOrderServiceApplication` 启动时检测 active profile，
    无 `demo` profile 时 `System.exit(1)` 并打印 ADR 索引路径
  - `application.yml` 显式声明 `spring.profiles.active: none`（默认禁用），
    `application-demo.yml` 声明 demo profile 真实端口、metrics、白名单
  - 根 `pom.xml` 把 `apps/demo-order-service` 从默认 `<modules>` 移走，新增
    `<profile id="demo">`（`activeByDefault=false`），需 `mvn verify -Pdemo` 显式启用
  - demo-order 测试 3 个原测试加 `@ActiveProfiles("demo")` 守门；新增
    `DemoOrderServiceApplicationProfileGuardTest`（6 个 case）覆盖静态守门方法
  - demo-order 测试：20/20 全绿（profile guard 6 + fault 7 + order 5 + zabbix 2）
  - 默认 reactor 25 modules；`-Pdemo` 26 modules（含 demo-order）

---

## 8. 缺 web/console（P1）

### 事实

- SKILL §4.1 与 AGENTS §3.1 要求 React + Vite + TypeScript + shadcn/ui + TanStack Query
- 仓库内 `web/` 与 `console/` 目录**不存在**
- 后端 1500+ 个 API 端点目前没有任何前端消费方

### 修复方案（Phase A 最小骨架）

```txt
1. 在 web/console 初始化 Vite + React 18 + TypeScript + shadcn/ui + Tailwind
2. 5 个最小页面：
   - /login         POST /api/auth/login
   - /              Dashboard（空卡片，调用 /api/auth/me）
   - /datasources   GET /api/datasources
   - /incidents     GET /api/incidents
   - /incidents/:id GET /api/incidents/{id}
3. 集中 src/api/client.ts：typed API 客户端，禁止页面内散落 fetch
4. shadcn add 锁版本：button、card、table、input、form、select、dialog、badge、empty、skeleton、sonner
5. 严格遵守 AGENTS §21.3~21.4 风格规范：
   - 不写 <div className="rounded-2xl bg-white p-6 shadow-sm"> 自封卡片
   - 不写 space-y-* / space-x-*
   - 颜色用 bg-primary / text-muted-foreground
6. Page Object 风格 smoke test：登录 → 看 Dashboard
```

### 状态

- **PR8 待办**（独立 PR，与后端 PR2~PR7 不混在同一次提交）

---

## 9. 文档分散（P1）

### 事实

- 58 个 markdown 散落在 `docs/mvp/`、`docs/scenarios/`、`docs/architecture/`、`docs/operations/`、`docs/deployment/`、`docs/integrations/`
- `roadmap.md` / `roadmap2.md` / `roadmap3.md` 三份路线图
- `docs/mvp/design/phaseX.Y.md` 与 `docs/scenarios/phase-zN-*.md` 命名风格不一致
- 顶层 `AGENTS.md` 与 Skill 都定义 6 个 Phase
- 缺 `docs/adr/`

### 修复方案

```txt
1. 选 SKILL.md 为单一真相源 → docs/mvp/roadmap.md 一句话指向 .agents/skills/aegisops/SKILL.md
2. 合并 roadmap.md / roadmap2.md / roadmap3.md → 留 roadmap.md，其他标 deprecated
3. 改造 docs/：
   docs/architecture.md      ← 从 SKILL §5 生成
   docs/data-model.md        ← 从 SKILL §6 生成
   docs/rca-design.md        ← 从 SKILL §11 生成
   docs/ai-agent-design.md   ← 从 SKILL §10 生成
   docs/automation-safety.md ← 从 SKILL §13 生成
   docs/mvp-roadmap.md       ← 单一指 SKILL §14
4. docs/adr/ 起始 0001-use-java-spring-boot.md、0002-mvp-fourth-app-justification.md（与 PR6 同步）
5. docs/scenarios/phase-z1-zabbix-demo-environment.md 与 SKILL §14 Phase 1 对齐：
   - 迁到 docs/phases/phase-1/2026-06-12-zabbix-ingestion.md
   - 旧路径标 deprecated
6. scripts/docs.ts check 必须全绿
```

### 状态

- **PR7 待办**

---

## 10. apps/aiops-agent（Python）未在文档中声明（P2）

### 事实

- `apps/aiops-agent/src/aiops_agent/service.py` 存在
- 没有任何文档说明 aiops-agent 与三 app 的边界
- 命名风格是 Python，但仓库其他后端是 Java

### 修复方案

```txt
1. 写 ADR 0003-aiops-agent-boundary.md：
   - aiops-agent 是 LLM 本地推理 sidecar（Ollama / vLLM 兼容）
   - 边界：仅通过 HTTP 暴露 OpenAI 兼容 chat/embeddings 接口
   - 不直接访问 PostgreSQL / MinIO
   - 部署：与 aiops-server 1:1 部署在 K8s 同一个 Pod 内
2. 在 docker-compose.yml 增加 aiops-agent 服务（profile=ai-local）
3. 在 AGENTS §3.2 注释中说明 aiops-agent 的存在与边界
```

### 状态

- **PR9 待办**

---

## 11. scripts/docs.ts 与 scripts/start.ts 职责混淆（P2）

### 事实

- `scripts/docs.ts` 生成 `docs/INDEX.md` + 验证 YAML frontmatter
- `scripts/start.ts` 一键 `docker compose up` + health check
- 两个脚本都是顶层 `package.json` 的 scripts 入口，但**职责说明不完整**

### 修复方案

```txt
1. 各自添加 README.md：
   scripts/docs.ts/README.md   # 描述 init / new / check / index 四个子命令
   scripts/start.ts/README.md  # 描述启动流程与超时
2. 在根 package.json scripts 注释每个 script 的边界
3. 不允许两边互相 import，独立维护
```

### 状态

- **PR10 待办**

---

## 12. PR 推进顺序

| PR        | 标题                                                              | 工作量   | 依赖                                             | 状态                                                    |
| --------- | ----------------------------------------------------------------- | -------- | ------------------------------------------------ | ------------------------------------------------------- |
| PR-mega-1 | AGENTS.md 全文重写（1733 → 441 行，与 SKILL.md 合并文档源头约定） | ~30 分钟 | 无                                               | **已完成（commit `f9fbe21`，L1）**                      |
| PR2       | RuntimePhase + health phase 标签 + Phase Discipline 守门          | 1.5 小时 | 无                                               | **已完成（commits `acf856e` + `02896d6` + 本 PR，L2）** |
| PR3       | Flyway V1 拆 5 个 V0001..V0005（clean slate）                     | 半天     | PR2 共享 RuntimePhase（不强依赖）                | **已完成（commit `4a36a3c`，L3）**                      |
| PR4       | runner → Application Service + ArchUnit 守门                      | 1~2 天   | 无                                               | **已完成（L2 refactor + L3 ArchUnit guard）**           |
| PR5       | worker 骨架（4 job + OutboxPoller + 调度链重构）                  | 2~3 天   | PR2（共享 RuntimePhase）、PR3（共享 V0006 迁移） | **已完成（L3 schema + L3 调度链重构）**                 |
| PR6       | demo-order-service ADR + profile                                  | 2 小时   | 无                                               | **已完成（ADR + Spring profile + Maven profile）**      |
| PR7       | docs 收敛 + roadmap 指向 SKILL.md                                 | 半天     | PR6（共享 ADR 目录）                             | 待办                                                    |
| PR8       | web/console Phase A（最小骨架）                                   | 2~3 天   | 无（独立仓库或子目录）                           | 待办                                                    |
| PR9       | aiops-agent ADR                                                   | 1 小时   | 无                                               | 待办                                                    |
| PR10      | scripts 拆分                                                      | 1 小时   | 无                                               | 待办                                                    |

## 13. 推进纪律

按 **AGENTS.md §1 第 15 条分级 PR 制度** 调整如下：

1. **每个改动先评估 PR 等级**（L1 / L2 / L3，按 AGENTS.md §1 第 15 条），并在 commit footer 标注 `PR-Tier: L1 / L2 / L3`
2. **L2 及以上需开分支**，分支命名 `fix/<scope>/<slug>`（如 `fix/infra/flyway-v0001-split`）；纯文档类 PR 用 `docs/<scope>/<slug`
3. **L2 及以上必须跑过五件套**（Java 工程，PR8 额外跑前端）：
   ```bash
   mvn -q -DskipTests compile
   mvn -q test
   mvn -q -DskipTests package
   pnpm -r --parallel run lint   # PR8 才有
   pnpm -r --parallel run build  # PR8 才有
   ```
   L1 纯文档类改动无需跑五件套，通过 `git diff --stat` + 人工 review + grep 自检验收
4. **自检清单**（提交前）：
   - [ ] 首行符合 `<type>(<scope>): 中文主题`
   - [ ] type/scope 在固定枚举内
   - [ ] 主题 ≤ 50 字
   - [ ] 正文列点，前缀「后端:」「前端:」「迁移:」「接口:」「安全:」
   - [ ] **L2+ 涉及 schema 变更**写「迁移: Vxxxx」并指明 Flyway 版本号（按 SKILL §17）
   - [ ] **L2+ 涉及接口变更**写「接口:」
   - [ ] **L2+ 涉及安全变更**写「安全:」
   - [ ] **L2+ 五件套全绿**
   - [ ] grep `@Deprecated` / `_legacy` / `legacy_` / `_old` / `old_` / `_previous` / `previous_` / `v1_` / `_v1` 0 命中（按 AGENTS.md §3.9 精炼关键字；Flyway V\d{4}\__init_\*.sql 不算 v1）
   - [ ] 文档类改动同步检查：被引用的 SKILL.md §X 章节仍存在且未改写
5. **风险与回滚**：L2+ PR 描述必须包含「风险与回滚」一节
6. **顺序约束**：PR5 依赖 PR2 + PR3，所以必须先做 PR2 / PR3
7. **Owner 审阅**：L3（跨 app / schema / 安全 / 公开 API）需 1 名 Owner + 1 名模块责任人审阅；L2 需 1 名 Owner 审阅；L1 不强制

## 14. 与第二轮回盘对应

| 回盘点出问题                                          | 本文件章节 | 优先级 | PR                                                |
| ----------------------------------------------------- | ---------- | ------ | ------------------------------------------------- |
| 文档源头冲突（已通过 AGENTS.md 全文重写合并两套约定） | §1         | P0     | **PR-mega-1 已完成（commit `f9fbe21`）**          |
| worker 空壳                                           | §2         | P0     | **PR5 已完成（L3 schema + L3 调度链重构）**       |
| 三 app 调度链断裂                                     | §3         | P0     | **PR5 已完成（与 §2 同 PR）**                     |
| health phase 标签错位                                 | §4         | P0     | **PR2 已完成（`acf856e` + `02896d6` + 本 PR）**   |
| Flyway 命名不符                                       | §5         | P1     | **PR3 已完成（commit `4a36a3c`）**                |
| runner 跨模块直接注 Repository                        | §6         | P1     | **PR4 已完成（L2 refactor + L3 ArchUnit guard）** |
| demo-order-service 不在三 app 列表                    | §7         | P1     | **PR6 已完成（ADR 0002 + profile 守门）**         |
| 缺 web/console                                        | §8         | P1     | PR8                                               |
| 文档分散                                              | §9         | P1     | PR7                                               |
| aiops-agent 未声明                                    | §10        | P2     | PR9                                               |
| scripts 职责混淆                                      | §11        | P2     | PR10                                              |

## 15. 验收

### 已完成验收（PR-mega-1）

```txt
1. AGENTS.md 行数: 1733 → 441, 压缩 74%
2. AGENTS.md 顶部 §0 「文档源头与覆盖关系」已写入
3. AGENTS.md 与 SKILL.md 无内容重复章节
4. AGENTS.md §2 Flyway commit 示例统一到 SKILL §17 命名 (V0006__init_*.sql)
5. AGENTS.md §3.9 自检关键字收紧到 8 项精炼, Flyway 版本号豁免已写入
6. grep 自检:
   - 旧 flyway 命名 phase2_incident_aggregation.sql 在 AGENTS.md 0 命中
   - "MVP 阶段不强制版本号" 在 AGENTS.md 0 命中
   - @Deprecated / legacy / v1 / old / previous 在 AGENTS.md 仅命中 §3.9 自检规则本身（必要, 应保留）
```

### 全量验收（全部 PR 合并后）

```txt
1. mvn -q verify 在 apps/aiops-server / apps/aiops-worker / apps/aiops-runner 全部通过
2. docker compose down -v && mvn spring-boot:run 跑通
3. Flyway 看到 5 个 init_*.sql 顺序执行
4. WorkerController、RunnerController health 返回 phase=phase5
5. pnpm -r build 在 web/console 跑通
6. scripts/docs.ts check 全绿
7. ARCH_UNIT 守门：runner 进程不允许 import *Repository
8. 集成测试：Zabbix event → outbox → worker pick → execution_run
```

- 不允许"跳过五件套"或"先合并再补测"
