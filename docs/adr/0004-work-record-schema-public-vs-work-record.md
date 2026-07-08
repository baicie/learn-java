---
title: 工作记录表 schema 命名（public vs work_record）阶段性决议
type: adr
status: accepted
phase: work-record
owner: ai
created: 2026-07-08
updated: 2026-07-08
related:
  - .agents/skills/aegisops/SKILL.md
  - .agents/skills/aegisops/SKILL.md §6.15.3
  - .agents/skills/aegisops/SKILL.md §17
  - apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql
  - apps/aiops-server/src/main/resources/db/migration/V0013__migrate_work_record_schema.sql
  - docs/reviews/work-record/2026-07-08-work-record-phase01-07-implementation-review.md
---

# ADR 0004: 工作记录表 schema 命名（public vs work_record）阶段性决议

## 状态

- **已接受（2026-07-08）**
- 触发来源：Phase 01–07 实施审查报告 §2.1 / §5
- 本 ADR 为**阶段性决议**，非最终形态

## 背景

- SKILL §6.15.3 明确要求："工作记录表使用 work_record.wr_template / work_record.wr_template_field / work_record.wr_record"。
- V0012（已发布）在 `public` schema 中创建 `wr_template / wr_template_field / wr_record` 三张表，注释明确写"所有表位于 public schema，命名带 wr_ 前缀"。
- V0013（已发布）仅创建空的 `work_record` schema，未迁移任何表。
- V0014 / V0015 继续以裸 `wr_*` 表名写入，意味着实际运行路径下（PostgreSQL 默认 `search_path = public, "$user"`），所有读写都落在 `public.wr_*`。
- `modules/aiops-work-record/.../DefaultTemplateInitializer.java` 同样使用裸 `wr_template` / `wr_template_field` 表名。
- jOOQ `DDLDatabase` 显式声明 `unqualifiedSchema=public`，物理上代码与 `public` schema 强绑定。
- 审查报告 §2.1 把"DefaultTemplateInitializer 访问 work_record.wr_template"作为 P0，与当前实际代码不一致——那只是 SKILL §6.15.3 的"应然"，而代码停留在 V0012 的"实然"。

## 决策

1. **当前阶段（Phase 01–07 收尾）维持 public schema**。
   - 不修改 V0012。
   - 撤销 V0013 中"work_record schema 容器"的隐含意义：将 V0013 的注释更新为"占位说明，public 为当前实际承载"，但保留 schema 本身（不 drop，避免与未来潜在引用冲突）。
   - 所有 Repository / Initializer / Test 继续使用裸 `wr_*` 表名，与 V0012 + jOOQ `unqualifiedSchema=public` 保持一致。
2. **真迁移到 `work_record` schema 推迟到独立 Phase（建议 Phase WR-S1: Schema Migration）**：
   - 一次性 `ALTER TABLE ... SET SCHEMA work_record`（包括 `wr_template / wr_template_field / wr_record / wr_record_*`）。
   - 重写 jOOQ `DDLDatabase` 配置：`unqualifiedSchema=work_record` 或显式 `<schemata>` 多 schema。
   - 同步所有 SQL 加 `work_record.` 前缀。
   - 补集成测试断言 schema 切换后表与代码一致。
3. **该推迟原因**：
   - 跨 6+ Repository、4+ Service、jOOQ codegen、Flyway 历史版本，影响面超出 Phase 01–07 收尾范围。
   - 真实生产环境 `public.wr_*` 与"应然" `work_record.wr_*` 的偏离属于技术债，本 ADR 把债固化、可追踪，不让它隐藏在 SKILL 与代码之间。
4. **新工作禁止引入"裸表名 + work_record. 混合"**：
   - 本决议有效期内，所有新代码默认与 V0012 一致：裸 `wr_*` 表名。
   - 任何把裸表名替换为 `work_record.wr_*` 的 PR 必须同时完成第 2 条的完整迁移，不允许半步状态。

## 备选

- **A. 立即全量迁移**：投入大、风险高、影响 4 个 Phase 之外的 jOOQ 自动化。**不采纳**（与用户决策一致）。
- **B. 撤销 V0013 的 schema 创建**：物理上删除 `work_record` schema 占位。**不采纳**：未来真迁移会再创建，撤销徒增迁移历史。
- **C. 维持当前事实但不固化为 ADR**（即维持现状，不显式承认矛盾）。**不采纳**：让矛盾继续隐藏在代码与 SKILL 之间，审查与新人 onboarding 都会再次踩坑。

## 影响

- 修复 P0-1（schema 迁移）由"必须修复"降级为"已记录的技术债"，由后续 Phase WR-S1 承接。
- 审查报告 §2.1 中关于"DefaultTemplateInitializer 访问 work_record.wr_template"的描述需要更正为"DefaultTemplateInitializer 访问 public.wr_template，与 V0012 + jOOQ 配置一致"。
- 未来 Phase WR-S1 启动时按本 ADR 第 2 条执行。

## 验证

- 启动 `apps/aiops-server`：Flyway 顺序执行 V0012 / V0013 / V0014 / V0015，落地表全部在 `public.wr_*`。
- `DefaultTemplateInitializer` 在 `ApplicationReadyEvent` 中能正确读取 `public.wr_template`。
- 单元测试在 H2 内存库中通过（`unqualifiedSchema=public` 解析一致）。
