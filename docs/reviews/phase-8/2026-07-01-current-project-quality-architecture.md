---
title: 当前项目质量与架构审查
type: review
status: draft
phase: phase-8
owner: ai
created: 2026-07-01
updated: 2026-07-01
related: []
---

# 当前项目质量与架构审查

## Review Target

本次审查目标是当前仓库整体工程质量、架构边界、质量门禁、安全与前端规范执行情况。

审查输入：

- `.agents/skills/aegisops/SKILL.md`
- `AGENTS.md`
- `.agents/skills/aegisops/references/*`
- 根 `pom.xml` / `package.json` / `.github/workflows/ci.yml`
- `apps/`、`modules/`、`web/console/`、`docs/`

当前阶段判断：

- SKILL.md §14 仍以 Phase 0 ~ Phase 6 为主线。
- 当前仓库文档和代码已出现 Phase 7 / Phase 8 / Phase Z9，并已有 ADR 解释 `aiops-agent` 与 `demo-order-service` 的边界。
- 因此本次按 `phase-8` 归档，但报告结论之一是：阶段定义与实际推进需要重新对齐。

## Summary

整体判断：项目已经不是“脚手架阶段”，而是一个具备可运行主链路、较多测试和多模块边界意识的 MVP 后期仓库。核心方向仍围绕 Incident、RCA、AI diagnosis、Runbook、Execution、Postmortem，主线没有跑偏。

但当前最大风险不在单点业务代码，而在工程治理层：

- CI 与根脚本引用不存在的 shell 文件，门禁存在直接失效风险。
- Java 版本、前端 lint 规则、根五件套脚本与 AGENTS.md / SKILL.md 不一致。
- Controller 大量散布在 `modules/*`，HTTP 层与领域/应用模块边界变薄。
- RBAC/权限粒度尚未真正落实到敏感写接口。
- 文档阶段已经推进到 Phase 8 / Z9，但主 Skill 仍停留在 Phase 0 ~ 6，后续 Agent 容易误判范围。
- 前端存在较多手搓 class、`space-y-*`、裸 `<button>`、raw color，且 ESLint 明确关闭了本项目要求的 Tailwind 规则。

## Findings

### P0 - Must Fix

#### P0-1 CI 和根脚本调用不存在的文件，当前质量门禁可能直接失败

证据：

- `.github/workflows/ci.yml` 调用 `bash scripts/ci/docs.sh` 与 `bash scripts/ci/backend.sh`。
- 根 `package.json` 的 `ci` / `ci:docs` / `ci:backend` 同样调用 `.sh`。
- 实际 `scripts/ci/` 下只有 `docs.ts`、`backend.ts`、`verify-local.ts`，没有 `docs.sh`、`backend.sh`、`verify-local.sh`。

影响：

- GitHub Actions 的 docs/backend job 在 clean checkout 后会找不到文件。
- 本地 `pnpm ci` 也会找不到 `scripts/ci/verify-local.sh`。
- 这会让 AGENTS.md §3.1 的 lint / format / typecheck / test / build 五件套无法成为真实门禁。

修复方向：

优先新增薄 shell wrapper，保持 workflow 和 package scripts 不大改，降低风险。

代码草案：

```bash
# scripts/ci/docs.sh
#!/usr/bin/env bash
set -euo pipefail

if ! command -v pnpm >/dev/null 2>&1; then
  corepack enable || true
fi

pnpm install --frozen-lockfile
pnpm exec tsx scripts/ci/docs.ts
pnpm exec tsx scripts/docs.ts check
```

```bash
# scripts/ci/backend.sh
#!/usr/bin/env bash
set -euo pipefail

pnpm exec tsx scripts/ci/backend.ts
```

```bash
# scripts/ci/verify-local.sh
#!/usr/bin/env bash
set -euo pipefail

pnpm exec tsx scripts/ci/docs.ts
bash scripts/ci/backend.sh
bash scripts/ci/frontend.sh
```

配套验证：

```bash
pnpm run ci:docs
pnpm run ci:backend
pnpm run ci:frontend
pnpm run ci
```

#### P0-2 Java 版本与项目技术栈不一致

证据：

- SKILL.md §4.2 明确使用 Java 21。
- `.github/workflows/ci.yml` 使用 `JAVA_VERSION: "21"`。
- 根 `pom.xml` 仍是 `<java.version>17</java.version>`。

影响：

- CI JDK 是 21，但编译 release 是 17，项目无法使用 Java 21 语言/API 能力。
- 后续模块可能因为本地/CI 版本理解不同而出现“能跑但不符合项目基线”的漂移。

修复方向：

把 Maven release 对齐到 21，并在 README / docs 中明确最低 JDK。

代码草案：

```xml
<!-- pom.xml -->
<properties>
  <java.version>21</java.version>
  <maven.compiler.release>${java.version}</maven.compiler.release>
</properties>
```

配套验证：

```bash
mvn -B -ntp -DskipITs=true -DskipE2E=true verify
```

#### P0-3 敏感写接口主要只有 authenticated，权限与审计边界不足

证据：

- `SecurityConfig` 对 `/api/**` 只要求 authenticated。
- `ExecutionController` 暴露创建、重试、取消自动化执行接口。
- `PluginController`、`AnsibleCredentialController`、`RunbookController` 等敏感接口应具备明确权限。
- `@EnableMethodSecurity` 已开启，但代码中缺少成体系的 `@PreAuthorize` / `PermissionGuard` 使用。

影响：

- 已登录用户可能访问不该访问的自动化、插件、凭据或管理接口。
- 自动化执行路径属于 AGENTS.md L3 高风险域，不能仅靠 Controller 内部约定。

修复方向：

先建立最小权限注解矩阵，不一次性重写所有安全模型。

代码草案：

```java
// modules/aiops-execution/src/main/java/io/aegisops/execution/ExecutionController.java
@PostMapping("/api/automation-plans/{planId}/executions")
@PreAuthorize("hasAuthority('automation:execute')")
public ApiResponse<ExecutionRunResponse> create(
    @PathVariable String planId,
    @RequestBody(required = false) ExecutionCreateRequest request) {
  return ApiResponse.ok(service.createExecution(TenantContext.requireTenantId(), planId, request));
}

@PostMapping("/api/executions/{executionId}/cancel")
@PreAuthorize("hasAuthority('automation:execute')")
public ApiResponse<ExecutionRunResponse> cancel(@PathVariable String executionId) {
  return ApiResponse.ok(service.cancel(TenantContext.requireTenantId(), executionId));
}
```

```java
// modules/aiops-plugin/src/main/java/io/aegisops/plugin/PluginController.java
@PostMapping("/api/plugins/{pluginId}/enable")
@PreAuthorize("hasAuthority('admin:manage')")
public ApiResponse<TenantPluginResponse> enablePlugin(...) {
  ...
}
```

配套测试草案：

```java
@Test
void createExecutionRequiresAutomationExecutePermission() throws Exception {
  mockMvc.perform(post("/api/automation-plans/plan_1/executions")
      .header("Authorization", tokenWithout("automation:execute"))
      .header("X-Tenant-Id", "tenant_1"))
    .andExpect(status().isForbidden());
}
```

### P1 - Should Fix

#### P1-1 Controller 大量散布在模块内，HTTP 层与领域边界偏薄

证据：

- 当前发现 39 个 `*Controller.java`。
- 大量 Controller 位于 `modules/aiops-*` 下，例如 `modules/aiops-alert/.../AlertController.java`、`modules/aiops-execution/.../ExecutionController.java`。
- `AlertController` 直接注入 `JdbcTemplate` 并写 SQL。

影响：

- domain/application 模块逐步承担 HTTP、SQL、DTO 拼装职责。
- OpenAPI、权限、审计、异常映射和 API versioning 会散落在各模块。
- 后续如果切 runner/server/worker 边界，Controller 跟着模块传播，边界更难收束。

修复方向：

采用两步迁移，避免大爆炸：

1. 先为现有 Controller 建 ArchUnit 守门和包命名约束：只有 `..web..`、`..controller..` 或 `apps/aiops-server` 可以出现 `@RestController`。
2. 后续逐模块把 Controller 移到 `apps/aiops-server` 或 `modules/aiops-web`，模块只暴露 Application Service。

代码草案：

```java
// apps/aiops-server/src/test/java/io/aegisops/server/ArchitectureControllerBoundaryTest.java
@AnalyzeClasses(packages = "io.aegisops")
class ArchitectureControllerBoundaryTest {
  @ArchTest
  static final ArchRule restControllersShouldLiveInWebBoundary =
      classes()
          .that().areAnnotatedWith(RestController.class)
          .should().resideInAnyPackage(
              "io.aegisops.server..",
              "io.aegisops.web..",
              "io.aegisops..controller..")
          .because("HTTP endpoints must stay in the server/web boundary");
}
```

抽取草案：

```java
// modules/aiops-alert/src/main/java/io/aegisops/alert/AlertQueryService.java
public class AlertQueryService {
  private final AlertRepository repository;

  public List<AlertEventRecord> listRecent(String tenantId, int limit) {
    return repository.listRecent(tenantId, Math.min(limit, 200));
  }
}
```

```java
// apps/aiops-server/src/main/java/io/aegisops/server/alert/AlertApiController.java
@RestController
@RequestMapping("/api/alerts")
class AlertApiController {
  private final AlertQueryService service;

  @GetMapping
  public ApiResponse<List<AlertEventRecord>> list() {
    return ApiResponse.ok(service.listRecent(TenantContext.requireTenantId(), 100));
  }
}
```

#### P1-2 前端 lint 配置与 AGENTS.md §3.3 / §3.4 相反

证据：

- `web/console/eslint.config.js` 中：
  - `tailwindcss/classnames-order: off`
  - `tailwindcss/no-custom-classname: off`
- 代码中存在多处 `space-y-*`、裸 `<button>`、手搓卡片、raw color，例如 `border-purple-200 bg-purple-50`。

影响：

- AGENTS.md 强制的 shadcn/Tailwind 规范无法自动守门。
- 新页面容易继续复制手搓 UI。

修复方向：

分两阶段：

1. 先把规则设为 `warn`，清理存量重点页面。
2. 清理后提升为 `error`，并在 CI 使用 `lint:ci` 阻断。

代码草案：

```js
// web/console/eslint.config.js
rules: {
  'tailwindcss/classnames-order': 'warn',
  'tailwindcss/no-custom-classname': [
    'error',
    {
      whitelist: [
        'bg-primary',
        'text-primary-foreground',
        'bg-muted',
        'text-muted-foreground',
      ],
    },
  ],
}
```

组件替换草案：

```tsx
// before
<button className="inline-flex h-7 items-center rounded-[min(var(--radius-md),12px)] border px-2.5">
  返回
</button>

// after
<Button asChild variant="outline" size="sm">
  <Link to="/incidents">
    <ArrowLeft data-icon="inline-start" />
    返回
  </Link>
</Button>
```

#### P1-3 根 package scripts 没有收敛 AGENTS.md 要求的五件套

证据：

- 根 `package.json` 当前只有 `build`、`lint`、`ci` 等，缺 `format`、`typecheck`、`test`。
- AGENTS.md §3.1 要求根 scripts 收敛 `lint / format / typecheck / test / build`。

影响：

- Agent 和人类开发者不知道该跑哪组命令。
- CI 和本地验证语义不一致。

修复方向：

根脚本明确代理到前后端对应脚本。

代码草案：

```json
{
  "scripts": {
    "lint": "pnpm -C web/console run lint",
    "format": "pnpm -C web/console run format:check && mvn -B -ntp spotless:check",
    "typecheck": "pnpm -C web/console run typecheck && mvn -B -ntp -DskipTests compile",
    "test": "pnpm -C web/console run test && mvn -B -ntp test",
    "build": "pnpm -C web/console run build && mvn -B -ntp -DskipTests package",
    "ci": "bash scripts/ci/verify-local.sh"
  }
}
```

#### P1-4 文档阶段、路线图与 Skill 主线发生漂移

证据：

- SKILL.md §14 只定义 Phase 0 ~ 6。
- `docs/INDEX.md`、`docs/mvp/**`、`docs/scenarios/**` 已出现 Phase 7、Phase 8、Phase Z9。
- `pyproject.toml` 标注 `version = "0.7.3"` 和 `Phase 7.3 agent memory`。

影响：

- Agent 按 SKILL.md 执行时会误以为 Phase 7/8/Z9 都是越级功能。
- Owner 评审也难判断“当前真实阶段”。

修复方向：

新增一个 accepted ADR 或修订 SKILL.md §14 / §21，将 Phase 7、Phase 8、Phase Z9 的定位写清楚：

- Phase 7：AI Agent workflow / memory / multi-agent，为 Phase 4 AI diagnosis 的扩展。
- Phase 8：SaaS 多租户、插件、私有化部署，为生产化硬化。
- Phase Z9：端到端 demo 验收，不是产品 phase。

代码草案：

```md
## Phase 7: AI Agent Workflow Hardening

Scope:

- LangGraph workflow modularization
- agent memory
- evaluation and checkpoint

Non-goal:

- direct automation execution

## Phase 8: Production Hardening

Scope:

- SaaS tenant hardening
- plugin policy
- private deployment / offline package

## Phase Z9: Demo Acceptance

Scope:

- Zabbix end-to-end demo scenario
- demo-order-service and mock fault injection
```

#### P1-5 `apps/aiops-agent/.venv` 出现在工作树扫描范围内

证据：

- `find apps modules web/console/src ...` 会扫出大量 `apps/aiops-agent/.venv/lib/...` 文件。
- `.gitignore` 已有 `.venv/`，所以提交风险相对可控，但本地工具扫描仍会被污染。

影响：

- Agent / rg / find / 统计脚本容易误把依赖源码当项目源码。
- 质量统计、测试发现、代码审查输出会被放大和噪声化。

修复方向：

本地虚拟环境移到仓库外，或统一命名到根 `.venv` 并让脚本显式排除。

代码草案：

```bash
# scripts/ci/lib-find-source.sh
find apps modules web/console/src \
  -path '*/.venv/*' -prune -o \
  -path '*/node_modules/*' -prune -o \
  -path '*/target/*' -prune -o \
  -type f -print
```

### P2 - Nice to Have

#### P2-1 SQL text block 与 JdbcTemplate 仍较多，持久化风格需要继续统一

现状：

- 项目已有 `aiops-persistence` 和 jOOQ 生成测试。
- 但部分简单 Controller / Service 仍直接写 SQL，例如 Alert 列表。

建议：

- 以 Incident、Execution、Runbook、Plugin 这类高价值域优先迁移到 Repository + jOOQ。
- 查询页可以暂缓，但不要继续新增 Controller 内 SQL。

代码草案：

```java
public interface AlertRepository {
  List<AlertEventRecord> listRecent(String tenantId, int limit);
}

public class JooqAlertRepository implements AlertRepository {
  public List<AlertEventRecord> listRecent(String tenantId, int limit) {
    return dsl.selectFrom(ALERT_EVENT)
        .where(ALERT_EVENT.TENANT_ID.eq(tenantId))
        .orderBy(ALERT_EVENT.STARTS_AT.desc())
        .limit(limit)
        .fetch(this::mapAlert);
  }
}
```

#### P2-2 `Map<String, Object>` 在 AI / evidence / execution JSON 边界较多

这类 Map 对外部 raw payload、JSONB metadata 有合理性，但应该限制在 adapter / JSON boundary，不进入主业务 DTO。

建议：

- 对稳定响应定义 record。
- 对 raw payload 用 `JsonNode` 或专用 `RawPayload` 类型包裹。

代码草案：

```java
public record AgentRawPayload(JsonNode value) {
  public static AgentRawPayload empty() {
    return new AgentRawPayload(JsonNodeFactory.instance.objectNode());
  }
}
```

#### P2-3 Python aiops-agent 需要纳入根 CI 五件套

现状：

- `apps/aiops-agent` 有大量测试和 ruff 配置。
- 根 CI 当前主要覆盖 Java 和前端，Python agent 没有统一入口。

建议：

新增 `scripts/ci/agent.sh`，并纳入 root `ci`。

代码草案：

```bash
#!/usr/bin/env bash
set -euo pipefail

cd apps/aiops-agent
python -m pip install -e '.[test]'
python -m ruff check src tests
python -m pytest -q
```

## Architecture Boundary Check

- [x] Incident remains central：主链路和文档仍围绕 Incident / RCA / diagnosis / execution / postmortem。
- [~] External systems are behind adapters：Zabbix、AI agent、evidence 有 adapter/client 意识；但模块内 Controller + SQL 仍需收敛。
- [x] AI does not directly execute production actions：ADR 0003 明确 aiops-agent 不触发 runner。
- [~] Risky actions require approval：Execution live mode 会查 approval snapshot；但 HTTP 权限粒度仍需补齐。
- [~] Tenant isolation is preserved：大部分 Repository/Controller 使用 tenantId；建议补充 ArchUnit/SQL contract 守门。
- [~] Audit logs are created for sensitive operations：安全过滤器和 execution audit 已有基础；敏感写接口仍需统一审计切面。

## Test Results

本次以静态审查为主，运行了以下验证/检查命令：

```bash
sed / rg / find / nl
npx tsx scripts/docs.ts new review current-project-quality-architecture --title "当前项目质量与架构审查" --phase phase-8 --status draft
```

未运行完整 `mvn verify`、`pnpm test`、`pnpm build`，原因是本轮目标是整体审查与文档产出，且已发现 CI wrapper 缺失问题。修复 P0-1 后应第一时间运行完整五件套。

## Final Verdict

当前项目方向是对的，代码量和测试资产也已经有一定厚度；真正需要优先修的是“守门系统”和“边界收束”。

建议按以下顺序拆 PR：

1. P0-1：补齐 CI shell wrapper 和根 `ci` 可运行性。
2. P0-2：Java 21 对齐。
3. P0-3：给 execution / plugin / credential / runbook 写接口补方法级权限。
4. P1-2 / P1-3：恢复前端 lint 规则和根五件套脚本。
5. P1-1：新增架构守门测试，再逐模块迁移 Controller。
6. P1-4：修订 SKILL / ADR，把 Phase 7 / 8 / Z9 纳入单一真相源。

PR-Tier 预估：

- CI wrapper + 根脚本：L2。
- Java 21：L2；若引发编译修复，保持 L2。
- 权限与审计：L3。
- Controller 边界迁移：L2 起步，涉及 execution / runner 路径时按 L3。
