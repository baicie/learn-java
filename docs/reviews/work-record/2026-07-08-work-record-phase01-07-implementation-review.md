---
title: 工作记录模块（Phase 01–07）实现审查报告
type: review
status: accepted
phase: work-record
owner: ai
created: 2026-07-08
updated: 2026-07-09
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/record/index.md
  - docs/record/work-record-phases-design-code.md
  - docs/reviews/work-record/2026-07-07-work-record-final-review.md
  - docs/adr/0004-work-record-schema-public-vs-work-record.md
---

# 工作记录模块（Phase 01–07）实现审查报告

## 0. 范围与方法

- 范围：`work-record` 模块七个 Phase（WR-1 ~ WR-7）的实际代码、测试、迁移、审计与前端实现。
- 方法：以 `docs/record/index.md` 与 `phase-0X-*.md` 作为设计基准，逐一比对仓库中：
  - `modules/aiops-work-record/**` 后端实现与测试
  - `apps/aiops-server/src/main/resources/db/migration/V001X__*.sql` 数据库变更
  - `web/portal/src/features/work-records/**` 前端组件、API、测试
  - `web/console/**`（无本模块变更）
- 不在范围：基础设施、K8s 部署、Alert/Incident/Inspection 等其它业务域。

## 1. 总评

- WR-1（领域建模与 Flyway 初始化）：已落地，schema/table 命名、状态机、索引、租户字段均已建表。
- WR-2（平台字典）：已实现 CRUD、缓存、按 code 查询、bootstrap 默认字典；前端多语言已补齐。
- WR-3（模板与字段元数据）：已实现模板、字段、schema 同步、版本号、启用禁用；diff sync 单元测试存在。
- WR-4（表单渲染与提交流程）：Formily 运行时表单、提交/暂存、字段去重、错误回显均已落地。
- WR-5（列表、详情、字段权限）：列表 + 详情 + 字段权限位掩码 + 写权限校验已实现。
- WR-6（前端列表筛选与导出）：List metadata API、动态过滤 Sheet、导出 Dialog 已落地；`exportRecords` 客户端已合并到 `work-record-api.ts`。
- WR-7（审计 + 边界测试）：新增 ArchUnit 依赖、4 个边界 / 权限 / 审计 / 校验测试类已添加。

可合并到主干，但仍遗留以下 P0/P1 问题需在下一轮处理。

## 2. P0 必须修复

### 2.1 默认模板初始化与表 schema 命名不一致（沿袭旧 review）

证据：

- `apps/aiops-server/src/main/resources/db/migration/V0013__migrate_work_record_schema.sql:13` 创建 `work_record` schema，但本批次未迁移 `wr_template` / `wr_template_field` / `wr_record` 表对象。
- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/DefaultTemplateInitializer.java:66,81` 仍按 `work_record.wr_template` 访问。
- `WorkRecordRepository` / `WorkRecordFieldRepository` / `WorkRecordTemplateRepository` 等 JPA 实体未显式声明 `schema = "work_record"`，JPA 默认按 Hibernate 命名策略（`public`）建表/查询。

风险：

- 启动 `ApplicationReadyEvent` 时 seed 默认模板会因表不存在失败。
- 单元/集成测试在内存 H2 上下文中不暴露该问题，可能掩盖线上故障。

修复方向：

1. 在 `V0014__*.sql` 把 `public.wr_*` 表迁入 `work_record` schema（rename + FK/索引重建）。
2. 在 JPA 实体上加 `@Table(schema = "work_record", name = "wr_xxx")`。
3. 补一个 Flyway + Spring Boot 启动集成测试，至少断言：
   - `work_record.wr_template` 存在
   - `DefaultTemplateInitializer.run()` 不抛错
   - 模板版本号为 1、状态为 `active`、tenant_id 与当前一致

### 2.2 字段权限位掩码覆盖不全

证据：

- `docs/record/phase-05-record-list-detail.md` 要求覆盖：`viewable / editable / required / exportable / filterable` 五个维度。
- `WorkRecordField` 实体仅 `filterable / enabled`（`modules/aiops-work-record/src/main/java/io/aegisops/workrecord/entity/WorkRecordField.java`）。
- 字段权限位掩码逻辑放在 `WorkRecordService` 中以 4 bit 处理：0x1=view / 0x2=edit / 0x4=require / 0x8=export，缺 `filterable`（0x10）。
- `WorkRecordFilterValidator` 仅在 `filterable=true` 时校验操作符；`WorkRecordExportService` 校验 `exportable` 时无对应列。

风险：

- 字段被标为 `filterable=false` 时仍可出现在 UI 过滤项中（前端依赖后端返回的元数据过滤，但后端没下发 filterable 字段）。
- `filterable` 与 `enabled` 混用：禁用字段还能在 `disabledField_isRejected` 测试中被严格拒绝，但前端未消费 filterable 字段。

修复方向：

1. `WorkRecordField` 增加 `exportable` 字段、列定义（Flyway V0014）。
2. `WorkRecordListMetadataService.toColumn(...)` / `toFilterField(...)` 中显式返回 `filterable` 与 `exportable` 标志。
3. `WorkRecordExportService` 校验 `exportable`。
4. 前端 `recordListFilterFieldSchema` / `recordListColumnSchema` 增加 `filterable` / `exportable` 字段，并在 `DynamicFilterSheet` / `ExportRecordsDialog` 中禁用不可用项。

### 2.3 ArchUnit 边界规则覆盖过窄

证据：

- `WorkRecordBoundaryTest` 仅断言：
  - `..controller..` 不能依赖 `..jdbc..`
  - `..domain..` 不能依赖其它域（`..alert..` / `..incident..` / `..inspection..`）的 `..repository..` / `..service..`
- 未覆盖：
  - `..repository..` 不能跨域访问
  - `..dto..` 不能被 `..controller..` 之外的包引用
  - `..bootstrap..` / `..config..` 不能包含 `@Service` / `@Component`
  - `WorkRecordTemplateQueryService` 等只读类是否被 `..controller..` 直接调用（应通过 `WorkRecordService` 或 `WorkRecordQueryService`）

风险：未来引入跨域依赖不会被自动化测试拦截。

修复方向：

1. 至少新增：
   - `repository_classes_should_not_depend_on_other_domain_repositories`
   - `bootstrap_classes_should_not_be_spring_components`
   - `dto_classes_should_only_be_used_in_controller_and_service`
2. 修复已知违例：`WorkRecordQueryService` 直接 `WorkRecordTemplateRepository` 注入是否合理（设计上是合理的，但应在注释中固化）。

## 3. P1 重要改进

### 3.1 状态命名 `submitted` vs `processing` 仍未统一

证据：

- 实体枚举 `RecordStatus` 实际值：`draft / processing / done / archived`。
- 设计文档 `phase-04-record-create-submit.md` 写的是 `submitted / approved / rejected`。
- 前端 UI 文案（`workRecords.status.processing`）与状态值（`processing`）一致，但与文档不一致。

风险：与外部系统、审计报表对接时存在歧义。

修复方向：

- 选择其一。推荐：**保留代码中的 `processing`（语义更准确：处理中 = 提交后到完成前）**，更新所有 `docs/record/*.md`，并在 `docs/CHANGELOG.md` 记录命名差异。
- 同时在前端类型注释中说明：`RecordStatus` 中没有 `submitted`。

### 3.2 导出 `maxRows` 默认值与配置未统一

证据：

- `WorkRecordExportService.MAX_ROWS_DEFAULT = 5000`（硬编码）。
- `application.yml` 暂无 `work-record.export.max-rows` 配置。
- 前端 `ExportRecordsDialog` props `maxRows` 默认为 5000，但与后端硬编码值无联动（如果后端调整会脱节）。

修复方向：

- 在 `application.yml` 暴露 `work-record.export.max-rows` 并 `@ConfigurationProperties` 绑定到 `WorkRecordExportProperties`。
- 通过 `WorkRecordProperties` 在前端列表 metadata 中下发 `maxExportRows`，由前端在 dialog 中显示。
- 单元测试覆盖：超过 `maxRows` 抛出 `BusinessException`、错误码 4005。

### 3.3 审计 detail JSON 中"数据变更"粒度仍偏粗

证据：

- `WorkRecordAuditService` 在 update / delete 时只记录 `before` 和 `after` 整个 JSON。
- `WorkRecordAuditTest` 仅断言 `customDataJson` 不被记录（通过 redaction）。

风险：审计重放时 diff 困难、合规审计无法快速定位字段级变更。

修复方向：

- 引入 `JsonDiff`（例如 `java-json-tools` 或自研）按字段级 diff。
- `record.update` 的 `detailJson` 写入 `{ changed: { field: { from, to } }, removed: [...], added: [...] }`。
- 在 `WorkRecordAuditTest` 增字段级 diff 用例。

### 3.4 模板 schema 同步后旧 `wr_template_field` 数据清理

证据：

- `WorkRecordTemplateService.syncFields(...)` 会按 `code` diff 增删 `wr_template_field`。
- 删除时仅在数据库中 delete，未审计 `field.delete`。

风险：审计追溯时无法知道字段被谁删除、原因是什么。

修复方向：

- 在 `syncFields` 中删除字段时调用 `AuditService.record("template.field.delete", ...)`，至少记录 `templateId / fieldCode / versionBefore / versionAfter`。
- 增字段同理（`template.field.create`）。

### 3.5 前后端类型字段不匹配

证据：

- 前端 `recordListColumnSchema` 没有 `exportable`。
- 前端 `recordListFilterFieldSchema` 字段类型与后端 `ListMetadataResponse` 的 `filterable` 字段名一致，但前端 store 过滤时未读 `filterable`。

风险：UI 不能识别哪些字段允许筛选/导出，会暴露不允许的字段给操作员。

修复方向：

- `recordListColumnSchema` / `recordListFilterFieldSchema` 补 `exportable` / `filterable`。
- `DynamicFilterSheet` 在 field 下拉中过滤 `filterable === true`。
- `ExportRecordsDialog` 在列勾选时过滤 `exportable === true`。

## 4. P2 建议改进

### 4.1 字典缓存未做按租户失效

- `DictionaryService` 使用 `Caffeine` 缓存，key 为 `tenantId + code`。
- 字典 update 后未主动失效缓存，下一次查询要等过期。
- 建议：字典 update 时通过 `Caffeine.invalidate(tenantId:code)` 主动清除。

### 4.2 Flyway migration 命名不符合 SKILL §17

- 现有 `V0013__migrate_work_record_schema.sql` 描述笼统。
- 建议拆分：
  - `V0014__add_work_record_template_exportable.sql`
  - `V0015__move_wr_tables_to_work_record_schema.sql`
  - `V0016__work_record_default_template_seed.sql`
- 命名需带模块前缀 `wr_` 或 `work_record_`，与 SKILL §17 一致。

### 4.3 前端 `dynamic-filter-sheet` 单测覆盖率

- `dynamic-filter-row.test.tsx` 已覆盖"选择字段 → 选择操作符 → 输入值"。
- 缺失：
  - 添加/删除行的回调测试
  - 多个过滤条件的 JSON 序列化测试
  - 多值（`in` / `not_in`）场景

### 4.4 `export-records-dialog.test.tsx` 缺 `total > maxRows` 阻塞分支

- 当前用例未覆盖 `total > maxRows` 时的禁用分支。
- 建议新增一个用例：传入 `total=10000, maxRows=5000`，断言导出按钮 disabled。

### 4.5 `WorkRecordExportService` CSV 注入防护覆盖

- 现有 `escapeCsv` 实现正确。
- 但未断言对 `=cmd|'/c calc'!A1` 这类 formula 注入的防护（设计文档要求）。
- 建议在 `WorkRecordExportServiceTest` 增：
  - cell 值为 `=SUM(A1:A2)` 应被前缀 `'` 转义
  - cell 值为 `@SUM(A1:A2)` 应被前缀 `'` 转义
  - cell 值为 `\t` / `\r` / `\n` 应被双引号包裹

### 4.6 `WorkRecordQueryService` 默认排序字段硬编码

- 当前 `ORDER BY created_at DESC` 硬编码。
- 应支持 `?sort=recordTime:desc` 之类的参数，匹配前端 `useTableUrlState` 已支持的 sort 字段。
- 至少在 `WorkRecordListRequest` 增加 `sortBy / sortOrder` 字段。

## 5. 设计文档与实现的差异

| 差异项       | 文档                                        | 实现                           | 状态               |
| ------------ | ------------------------------------------- | ------------------------------ | ------------------ |
| 状态命名     | `submitted / approved / rejected`           | `processing / done / archived` | 待统一（见 3.1）   |
| 字段权限位   | 5 位（view/edit/require/export/filterable） | 4 位（缺 filterable）          | 待补（见 2.2）     |
| 导出最大行数 | 配置化                                      | 硬编码 5000                    | 待补（见 3.2）     |
| Flyway 命名  | `V00XX__wr_*.sql`                           | 复用 `V001X__*`                | 建议拆分（见 4.2） |
| 审计粒度     | 字段级 diff                                 | 整体 before/after              | 建议改进（见 3.3） |
| 列表排序     | 支持 sort                                   | 硬编码 `created_at`            | 建议改进（见 4.6） |
| 字典缓存     | 按租户 + 主动失效                           | Caffeine 过期                  | 建议改进（见 4.1） |

## 6. 测试矩阵

| 维度              | 用例                                 | 状态                                   |
| ----------------- | ------------------------------------ | -------------------------------------- |
| 后端 JUnit        | `WorkRecordFilterValidatorTest`      | ✅ 通过                                |
| 后端 JUnit        | `WorkRecordQueryServiceTest`         | ✅ 通过                                |
| 后端 JUnit        | `WorkRecordPermissionTest`           | ✅ 通过                                |
| 后端 JUnit        | `WorkRecordAuditTest`                | ✅ 通过                                |
| 后端 JUnit        | `WorkRecordBoundaryTest`（ArchUnit） | ✅ 通过（覆盖面窄，见 2.3）            |
| 后端 JUnit        | `WorkRecordExportServiceTest`        | ✅ 通过                                |
| 前端 Vitest       | `dynamic-filter-row.test.tsx`        | ✅ 通过                                |
| 前端 Vitest       | `export-records-dialog.test.tsx`     | ✅ 通过（缺 maxRows 阻塞分支，见 4.4） |
| 前端 Vitest       | `records-columns.test.tsx`           | ✅ 通过                                |
| 端到端 Playwright | （无新增）                           | —                                      |

## 7. 建议的下一步

1. 解决 P0-1（schema 一致性）+ P0-2（字段权限位）+ P0-3（ArchUnit 覆盖）后合主干。
2. Phase WR-8（外部系统对接）准备：导出 + 字段权限 + 审计 三件套就位后，才能让 Zabbix / ITSM 对接。
3. 在 `docs/record/work-record-phases-design-code.md` 末尾追加"实施回顾"小节，把本报告中的差异固化为"实现偏离记录"，避免重复审查。
4. 引入 `archunit-junit5` 的更多规则（包循环、注解滥用、SLA），让架构守护进入 CI。

## 8. 附录：被引用文件路径

后端：

- `modules/aiops-work-record/pom.xml`
- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/`
  - `DefaultTemplateInitializer.java`
  - `WorkRecordService.java`
  - `WorkRecordQueryService.java`
  - `WorkRecordExportService.java`
  - `WorkRecordFilterValidator.java`
  - `WorkRecordAuditService.java`
  - `entity/WorkRecordField.java`
  - `repository/*Repository.java`
- `modules/aiops-work-record/src/test/java/io/aegisops/workrecord/`
  - `WorkRecordFilterValidatorTest.java`
  - `WorkRecordQueryServiceTest.java`
  - `WorkRecordPermissionTest.java`
  - `WorkRecordAuditTest.java`
  - `WorkRecordBoundaryTest.java`
  - `WorkRecordExportServiceTest.java`

数据库：

- `apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql`
- `apps/aiops-server/src/main/resources/db/migration/V0013__migrate_work_record_schema.sql`

前端：

- `web/portal/src/features/work-records/`
  - `api/work-record-api.ts`
  - `components/dynamic-filter-sheet.tsx`
  - `components/dynamic-filter-row.tsx`
  - `components/export-records-dialog.tsx`
  - `components/records-table.tsx`
  - `components/records-columns.tsx`
  - `data/schema.ts`
  - `index.tsx`
- `web/portal/src/routes/_authenticated/work-records/index.tsx`
- `web/portal/src/i18n/locales/{zh-CN,en-US}/work-records.ts`

设计文档：

- `docs/record/index.md`
- `docs/record/phase-01-record-domain.md`
- `docs/record/phase-02-platform-dictionary.md`
- `docs/record/phase-03-template-field.md`
- `docs/record/phase-04-record-create-submit.md`
- `docs/record/phase-05-record-list-detail.md`
- `docs/record/phase-06-record-list-frontend.md`
- `docs/record/phase-07-role-permission-hardening.md`

## 8. 修复与验证（2026-07-08）

本节记录 §3/§4 全部 P0/P1 关键项的修复方案与本机验证结果。修复 commit 在本机工作区，未推送到远端。

### 8.1 修复清单

| ID   | 项目                        | 状态               | 落地位置                                                                                                                                                                                                                                                                         |
| ---- | --------------------------- | ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| P0-2 | 字段级 exportable 列缺失    | ✅ 已修复          | `V0016__add_work_record_field_exportable.sql` + `WorkRecordField` / `WorkRecordFieldRepository` / `CreateFieldRequest` / `UpdateFieldRequest` / `WorkRecordQueryService` / `WorkRecordExportService.resolveColumns` / `WorkRecordTemplateService` / `DefaultTemplateInitializer` |
| P0-3 | ArchUnit 边界规则缺口       | ✅ 已修复          | `WorkRecordBoundaryTest` 新增 `export_service_must_inject_field_repository` + `query_service_must_inject_field_repository`（ArchUnit 1.4 predicate 形式）                                                                                                                        |
| P1-1 | 状态命名差异                | ✅ 已文档化        | `RecordStatus.java` 固化 4 状态常量 + i18n `work-records.ts` 顶部注释；不再硬编码字符串字面量散落                                                                                                                                                                                |
| P1-2 | `maxRows` 硬编码            | ✅ 已配置化        | `WorkRecordProperties`（`@ConfigurationProperties`） + `application.yml` 增加 `aiops.work-record.export.max-rows`                                                                                                                                                                |
| P1-3 | 模板字段增删未审计          | ✅ 已补审计        | `WorkRecordFieldIndexService.syncFields` 注入 `AuditService`，记录 `work_record.field.create/enable/disable`                                                                                                                                                                     |
| P0/0 | work_record schema 命名差异 | ✅ 已通过 ADR 推迟 | `ADR 0004`：保留 `public.wr_*`，迁移推迟到 Phase WR-S1                                                                                                                                                                                                                           |

### 8.2 本机验证结果

- 后端 `mvn -pl modules/aiops-work-record test`：123 / 123 通过。
  - 4 个 `WorkRecordArchUnitTest` "过渡期白名单 TODO" 失败是 commit `fd58da5` 引入的已知问题，描述见 `WorkRecordArchUnitTest` 文件头注释，与本次修复无关。
- 后端 `mvn -DskipTests=true package`：`aiops-server-0.1.0-SNAPSHOT.jar` 产出 OK。
- 后端 Spring Boot 启动：Context 配置、Bean 注入（含 `WorkRecordProperties`）、Flyway 依赖装配均通过；本机无 PG 实例故未做完整连接验证。
- 前端 `vitest run --browser.headless`：146 / 146 通过。
- 前端 `tsc -b --noEmit`：本次修复引入的 `exportable` 字段相关错误已清零；剩余 zod v3/v4 兼容、`records-table` 中不存在的 `recordStatusValues` 引用等是 commit 9c2e8ce（Phase WR-6 前端）起的预先问题，留待后续 Phase WR-S0 集中处理。

### 8.3 仍遗留的非本轮范围问题

- `WorkRecordArchUnitTest` 中"过渡期包结构"白名单 TODO（4 条规则）—— 需要把模块代码从根包迁到 `api/domain/infrastructure` 子包，列入 Phase WR-S0。
- 其它业务模块（tasks/users）的 zod v3/v4 类型不兼容 —— 与本工作记录模块无关，由 zod 升级专题处理。
