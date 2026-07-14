---
title: Portal 源码归位与工作记录闭环实施计划
type: design
status: accepted
phase: phase-21
owner: ai
created: 2026-07-14
updated: 2026-07-14
related:
  - docs/adr/0007-portal-route-oriented-source-layout.md
  - .agents/skills/portal/SKILL.md
---

# Portal 源码归位与工作记录闭环实施计划

> **执行要求：** 使用 test-driven-development 逐项完成；每个行为变更先看到测试失败，再写最小实现。

**目标：** 在当前 HEAD 上删除 `web/portal/src/features`，保留并补齐登录、IAM、字典、日历、多模板、设计器、运行态和列表闭环。

**架构：** 路由页面放 `src/pages`，HTTP 契约放 `src/api`，鉴权放 `src/auth`，复用业务组件放 `src/components`，查询 Hook 放 `src/hooks`，纯模型与转换放 `src/lib`。后端继续复用现有 `aiops-security`、`aiops-platform`、`aiops-work-record` 和 V0012–V0028 migration，不复制业务实现。

**技术栈：** React 19、TanStack Router/Query/Table、shadcn/ui、Vitest Browser、Spring Boot、Flyway、OpenAPI。

---

## 文件归位

| 原目录                                                                 | 目标目录                        |
| ---------------------------------------------------------------------- | ------------------------------- |
| `src/features/auth`                                                    | `src/auth`                      |
| `src/features/dashboard`                                               | `src/pages/dashboard`           |
| `src/features/errors`                                                  | `src/pages/errors`              |
| `src/features/settings`                                                | `src/pages/settings`            |
| `src/features/iam/api`                                                 | `src/api/iam`                   |
| `src/features/iam/hooks`                                               | `src/hooks/iam`                 |
| `src/features/iam/components`                                          | `src/components/iam`            |
| `src/features/dictionaries/api.ts`                                     | `src/api/dictionaries.ts`       |
| `src/features/dictionaries/hooks`                                      | `src/hooks/dictionaries`        |
| `src/features/dictionaries/components`                                 | `src/components/dictionaries`   |
| `src/features/calendars/api.ts`                                        | `src/api/calendars.ts`          |
| `src/features/work-records/api`                                        | `src/api/work-records`          |
| `src/features/work-records/hooks`                                      | `src/hooks/work-records`        |
| `src/features/work-records/{components,designer,formily,list,runtime}` | `src/components/work-records/*` |
| `src/features/work-records/*.tsx`                                      | `src/pages/work-records`        |
| `src/features/work-records/data`                                       | `src/lib/work-records`          |

## Task 1：建立失败守卫

- [x] 新增 `scripts/ci/check-portal-no-features.mjs`，当 `src/features` 存在或源码 import `@/features/` 时返回非零。
- [x] 新增 `scripts/ci/check-portal-ui-primitives.mjs`，禁止页面和业务组件直接使用原生按钮、输入、选择、表格与 `@radix-ui/*`。
- [x] 运行两个脚本，确认它们分别因现有目录和现有原生控件失败。

验证：

```bash
node scripts/ci/check-portal-no-features.mjs
node scripts/ci/check-portal-ui-primitives.mjs
```

预期：迁移前失败；迁移后通过。

## Task 2：迁移目录和 import

- [x] 使用 `git mv` 按“文件归位”表迁移全部源码与同目录测试。
- [x] 将 `@/features/auth` 改为 `@/auth`，其余引用改为新的 `api/pages/components/hooks/lib` 路径。
- [x] 删除不再被路由或源码引用的模板 `tasks/users/roles` 副本。
- [x] 更新 `vite.config.ts` 覆盖率 include，确保不再引用 `src/features`。
- [x] 运行类型检查，逐个修复路径错误，不添加兼容转发层。

验证：

```bash
pnpm -C web/portal run typecheck
node scripts/ci/check-portal-no-features.mjs
```

预期：均退出 0，且 `test ! -d web/portal/src/features`。

## Task 3：补齐模板管理与独立设计器路由

- [x] 先新增模板 API 与模板管理页面测试，覆盖创建、编辑、复制、启停、归档、版本查看和权限隐藏。
- [x] 扩展 `src/api/work-records/templates.ts`，直接复用后端已有模板端点。
- [x] 新增 `src/pages/work-records/templates.tsx` 与最小 shadcn 表格/对话框组件。
- [x] 新增 `/work-records/templates` 和 `/work-records/templates/$templateId/designer` 文件路由。
- [x] 将设计器的模板选择状态改为路由参数；返回按钮回模板列表。
- [x] 删除旧 `/work-records/designer` 路由，不保留双入口。

验证：

```bash
pnpm -C web/portal run test -- templates
pnpm -C web/portal run typecheck
```

预期：模板行为测试和路由类型检查通过。

## Task 4：统一 UI 原语

- [x] 先让 UI 守卫报告现有违规文件。
- [x] 字典、IAM、工作记录列表统一改用 shadcn `Table`。
- [x] 设计器预览和运行态统一改用 `Input`、`Textarea`、`Checkbox`、`Select`。
- [x] 业务组件不直接 import `@radix-ui/*`；`src/components/ui` 安装产物继续允许。
- [x] 测试样例 DOM 不纳入生产源码原语限制。

验证：

```bash
node scripts/ci/check-portal-ui-primitives.mjs
pnpm -C web/portal run lint
```

预期：无违规并退出 0。

## Task 5：同步规则、接口与构建

- [x] 新增 ADR 0007，记录从 feature-first 切换为 route-oriented 分层。
- [x] 更新 Portal Skill 的目录、路由、测试与接入约定，删除 `features/<feature>` 强制规则。
- [x] 更新 AegisOps Skill 工作记录前端边界，明确 Portal 是当前入口和模板版本已进入现状。
- [x] 将两个守卫加入 `scripts/ci/frontend.sh`，不另建重复流水线。
- [x] 核对 Spring Controller 与生成 OpenAPI；现有端点完整，并补充 `docs/api/work-record-templates.md`。
- [x] 更新受迁移影响的 Phase 20 路径说明；文档索引继续由既有脚本生成。

验证：

```bash
bash scripts/ci/docs.sh
bash scripts/ci/frontend.sh
```

预期：文档与 Portal 门禁通过。

## Task 6：完整验收

- [ ] 清理损坏的可选原生依赖后重新安装锁文件对应依赖。
- [ ] 运行 Portal lint、typecheck、test、knip、build。
- [ ] 运行 `aiops-security`、`aiops-platform`、`aiops-work-record` 后端测试。
- [ ] 运行完整本地验证并记录任何环境性阻断，不把未运行项目表述为通过。

验证：

```bash
bash scripts/ci/verify-local.sh
```

预期：退出 0；若外部环境阻断，保留前述分项验证证据并明确报告。

验收记录：Portal format、lint、typecheck、257 项测试、覆盖率门槛和 build 已通过。完整 docs/backend 门禁仍被本次修改前已存在的旧文档 frontmatter 缺失与 Java 源码 Spotless 违规阻断；`knip` 仍报告模板自带的未引用素材和通用导出，均已在交付说明中保留事实。
