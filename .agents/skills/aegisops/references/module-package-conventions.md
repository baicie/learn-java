---
title: 模块内包结构与依赖方向
type: architecture
status: accepted
phase: global
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - .agents/skills/aegisops/SKILL.md §3
  - .agents/skills/aegisops/references/architecture-boundaries.md
---

# 模块内包结构与依赖方向

本文件是 SKILL §3 与 `references/architecture-boundaries.md` 的实操补充。当前仓库部分模块
（aiops-incident、aiops-execution、aiops-ai-client、aiops-evidence）已经把业务层和
Spring Web 混在一起；本规范为后续新增与重构提供唯一模板，避免 AI 在已有边界上再造平行分支。

## 1. 适用模块

```txt
强约束: aiops-datasource / aiops-asset / aiops-alert / aiops-incident
         aiops-evidence / aiops-rca / aiops-report / aiops-inspection
         aiops-runbook / aiops-integration / aiops-plugin
         aiops-work-record

推荐约束 (按本规范整改, 不强制 Phase 1 内全部完成):
  aiops-ai-client
  aiops-execution

暂不约束 (承担基础设施角色, 允许扁平):
  aiops-common / aiops-persistence / aiops-web / aiops-security
  aiops-tenant / aiops-user / aiops-audit / aiops-observability
  aiops-platform
```

## 2. 标准包结构

```txt
modules/<name>/src/main/java/io/aegisops/<name>/
├── api/                  # 对外入口: RestController + Request/Response DTO
│   ├── <Name>Controller.java
│   ├── dto/              # 仅本模块 api 用的 DTO, 不跨模块
│   └── internal/         # 内部 /internal/* 入口 (供其它 app 或 aiops-agent 调用)
│
├── application/          # 用例编排 + ApplicationService facade
│   ├── <Name>ApplicationService.java       # 对外暴露, 跨模块只看到这层
│   ├── <Name>QueryService.java             # 只读 facade, 可选
│   └── job/                                # 后台任务, 由 worker 调度
│
├── domain/               # 领域对象, 状态机, 规则, 领域服务
│   ├── model/                              # 实体 / 值对象
│   ├── event/                              # 领域事件
│   └── rule/                               # 规则引擎 / 策略
│
├── infrastructure/       # 持久化 + 外部系统 + 跨切关注
│   ├── persistence/                        # *Repository 接口 + *Jooq/Jdbc* 实现
│   ├── adapter/                            # 外部系统 client (HTTP/RPC)
│   └── config/                             # Spring @Configuration
│
├── dto/                  # 跨层或跨模块共享 DTO (注意: 当前 aiops-execution 用此包)
│
└── internal/             # 仅本模块内部使用, 不允许跨模块依赖
```

强制规则:

```txt
1. domain 包不得 import org.springframework.web.* / springdoc / openapi 任何东西
2. api 包不得 import org.springframework.jdbc.* / org.jooq.* (受 ControllerPersistenceBoundaryTest 保护)
3. application 包允许依赖 domain + infrastructure, 不允许 import 其它模块的 Controller
4. 跨模块对外契约只允许两类:
     - application/<Name>ApplicationService.java      (主推荐)
     - dto/<Name>*Record.java / <Name>*Command.java   (与 ApplicationService 配套)
5. infra 模块 (aiops-*-adapter) 同样适用本结构, 但 application 退化或省略
```

## 3. 当前模块偏差清单 (Phase Z9 后整改)

| 模块               | 当前偏差                                                        | 整改建议                                                                                                                               |
| ------------------ | --------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| aiops-evidence     | domain 与 Controller 同包根目录; 引入 `spring-boot-starter-web` | 把 `EvidenceController` 等迁入 `api/`; `application.yml` 中 starter-web 仅留 actuator 必要, 改为 starter-webflux 或保持但只在 api 包用 |
| aiops-ai-client    | 同上                                                            | 拆 `api/HttpAiAgentController` + `application/AiAgentOrchestrator`, 但 starter-web 仅给 api 包用                                       |
| aiops-execution    | 224 个 Java 类, Controller 与 Repository 同包根目录             | 拆 `api/` (ExecutionController 等) + `infrastructure/persistence/` (Jooq*) + `service/` 保持 facade                                    |
| aiops-incident     | Controller 与 Jdbc* 同包根目录                                  | 拆 `api/IncidentController` + `infrastructure/persistence/JdbcIncidentRepository`                                                      |
| aiops-runner (app) | Starter-web 仅用于 `/internal/runner/status`, 合理              | 维持现状, 但要保证 executor 包内禁止直接依赖 ExecutionRepository (ArchUnit 已覆盖)                                                     |
| aiops-worker (app) | Starter-web 仅用于 `/internal/worker/status`, 合理              | 维持现状, 后续可在 api 包统一 /internal/* 入口                                                                                         |

## 4. ArchUnit 推荐测试

新增模块 (或整改老模块) 后, 在 `apps/aiops-server/src/test/java/io/aegisops/server/` 下追加:

```java
// 推荐样例: aiops-evidence
class EvidenceArchUnitTest {

  @Test
  void evidenceControllersMustNotDependOnJdbcOrJooq() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("io.aegisops.evidence.api..")
        .should().dependOnClassesThat().areAssignableTo(JdbcTemplate.class)
        .orShould().dependOnClassesThat().areAssignableTo(DSLContext.class);
    rule.check(classes);
  }

  @Test
  void evidenceDomainMustNotDependOnAdapterOrWeb() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("io.aegisops.evidence.domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "io.aegisops.zabbix.adapter..",
            "io.aegisops.web..",
            "org.springframework.web..");
    rule.check(classes);
  }
}
```

CI 入口仍是 `bash scripts/ci/backend.sh` (mvn verify)。

## 5. 跨模块调用检查清单 (评审必查)

```txt
- [ ] 在模块 A 是否注入了模块 B 的 *Repository / *Dao / *Jdbc* / *Jooq*?
       -> 必须改为 *ApplicationService / *QueryService / *ReadService
- [ ] 在模块 A 是否 import 了模块 B 的 *Controller?
       -> 必须改为 ApplicationService, 或拆出 *InternalApi 包
- [ ] 在模块 A 的 domain 包是否 import 了 -adapter / -web / spring-web?
       -> 必须移到 application 或 api 包
- [ ] 在 apps/aiops-runner 是否新增了直接读 ExecutionRepository 的代码?
       -> 改走 ExecutionApplicationService, 更新 RunnerArchUnitGuardTest 白名单
- [ ] 是否在 starter-web 业务模块的 domain 包里 @RestController 了?
       -> 移到 api 包
```

## 6. 与其它规范的关系

```txt
冲突优先级:
  1. 本文件
  2. references/architecture-boundaries.md
  3. SKILL.md §3

不冲突项, 同步生效:
  - docs/adr/0003-aiops-agent-boundary.md (Python Agent 边界, 不影响 Java 包结构)
  - references/automation-safety.md (风险等级, 在 execution 包结构内仍生效)
```
