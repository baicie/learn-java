---
title: 工作记录模块规则沉淀后实现审查
type: review
status: draft
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - .agents/skills/aegisops/SKILL.md
  - docs/record/index.md
  - docs/record/work-record-phases-design-code.md
  - docs/reviews/work-record/2026-07-06-work-record-implementation-review.md
---

# 工作记录模块规则沉淀后实现审查

本次审查先从两份讨论材料中提取稳定规则，并已持久化到 `.agents/skills/aegisops/SKILL.md` 的 `§6.15 工作记录模块规则`。随后按新规则审查当前工作区实现。

## 已持久化的规则摘要

- 工作记录是轻量可配置记录能力，不是工单系统、流程引擎或低代码平台。
- 后端边界固定为 `modules/aiops-work-record`，平台字典固定为 `modules/aiops-platform/.../dictionary`。
- 前端放在现有 `web/console`，不做微前端；工作记录 API 不继续塞进全局 `client.ts`。
- 第一版同库独立 `work_record` schema，工作记录表使用 `work_record.wr_template`、`work_record.wr_template_field`、`work_record.wr_record`。
- 字典项、表单字段、模板删除优先禁用或软删除，不物理删除。
- `field_code` 与 `field_type` 创建后不可随便修改，且必须拒绝保留字段编码。
- 保存记录时内置字段放主表，自定义字段放 `custom_data_json`，后端必须校验动态字段。
- 普通用户只能查自己的记录；管理员/记录管理员才能查全部。
- 列表必须分页，导出必须限制最大行数，导出不能把 JWT 放进 URL。
- 第一版不引入完整 LowCodeEngine；Formily / Designable 只作为后续复杂度上升后的评估项，不是当前默认依赖。

## 已改善的点

- 旧 migration 文件名已不再显示为删除状态，并在 `FlywayMigrationVersionUniquenessTest` 增加了历史文件白名单。
- CSV 导出已改为 `downloadFile` 使用 `Authorization` header，不再把 token 放进 URL。
- 工作记录读取权限已拆成 `work-record:read:self` 与 `work-record:read:all`。
- 字典、模板、字段、记录增加了 update 能力和部分审计记录。
- JSON 字段增加了 `JsonPayloads` 校验，避免非法 JSON 直接落到数据库异常。
- 前端已拆出 `features/platform-dictionary`，平台字典不再复用 work-record 私有 API 文件。

## 发现

### P1：工作记录表仍在 public schema，违反新持久化的同库独立 schema 规则

证据：

- [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:4) 注释明确写“所有表位于 public schema”。
- [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:42) 创建的是 `wr_template`，不是 `work_record.wr_template`。
- [WorkRecordRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordRepository.java:25) 查询的是 `wr_record`。

影响：

工作记录模块的数据边界没有被数据库 schema 固化，后续独立迁移、权限隔离、对象搜索和备份都会更难。并且这直接违反 `.agents/skills/aegisops/SKILL.md §6.15.3`。

修复思路：

- `V0012` 中增加 `create schema if not exists work_record;`。
- 将三张工作记录表迁移到 `work_record.wr_template`、`work_record.wr_template_field`、`work_record.wr_record`。
- Repository SQL 全部加 schema 前缀。
- `jooq-codegen.xml` includes 改为能识别 `work_record` schema 下的表；如 H2/jOOQ 生成受限，应修 codegen 配置或测试适配，不要牺牲运行时模型边界。

### P1：更新记录时省略 JSON 字段会被覆盖成空对象

证据：

- [WorkRecordService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordService.java:113) 当请求未传 `builtinDataJson/customDataJson` 时传给 repository 的值为 `null`。
- [WorkRecordRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordRepository.java:124) 又调用 `blankJson(request.builtinDataJson())`。
- [WorkRecordRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordRepository.java:155) `blankJson(null)` 返回 `"{}"`，导致 SQL `coalesce(?::jsonb, builtin_data_json)` 总是拿到非空 `{}`。

影响：

只更新标题或状态时，原有 `custom_data_json` 会被清空，动态字段数据丢失。

修复思路：

- 更新路径不要使用 `blankJson`。新增 `nullableJson`，请求为 `null` 时返回 `null`，只有创建时才把空值规范为 `{}`。
- 为 `WorkRecordRepository.update` 加单元测试：只传 `title` 时不覆盖 `custom_data_json`。
- 同类问题也存在于字段更新，见下一个问题。

### P1：更新字段时省略 `optionsJson` 会覆盖成空数组

证据：

- [WorkRecordTemplateService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordTemplateService.java:166) `optionsJson == null` 时传入 normalized request 的 `optionsJson` 为 `null`。
- [WorkRecordFieldRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldRepository.java:92) 更新时调用 `blankArray(request.optionsJson())`。
- [WorkRecordFieldRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldRepository.java:131) `blankArray(null)` 返回 `"[]"`。

影响：

修改字段名称、排序或启用状态时，会把已有静态选项清空，导致历史表单渲染和新记录填写异常。

修复思路：

- 更新路径新增 `nullableArray`，`null` 保持 SQL `coalesce(null::jsonb, options_json)`。
- 补 repository/service 测试：更新 `fieldName` 不应清空 `options_json`。

### P1：记录保存仍未按模板字段做业务校验

证据：

- [WorkRecordService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordService.java:64) 只校验 JSON 是对象。
- 未看到保存记录时读取 `wr_template_field` 校验 required、field type、select/multi_select 合法值、字段是否属于模板、禁用字段是否被写入。

影响：

前端绕过后，任意字段、错误类型、非法选项都可以进入 `custom_data_json`，动态表单会很快积累脏数据。这是工作记录模块长期可维护性的核心风险。

修复思路：

- 在 `WorkRecordService.create/update` 注入字段查询端口，按 `templateId` 读取启用字段定义。
- 校验 `customDataJson` 中所有 key 必须存在于模板字段。
- 校验 required、number/date/datetime/select/multi_select/user/switch 类型。
- `dict` 来源字段需通过字典服务校验 value 是否存在；展示历史时可包含禁用项，但新写入不能选择禁用项。
- 补测试：required 缺失、select 非法值、multi_select 非法值、禁用字段写入。

### P1：菜单种子路径仍有 404，图标 key 也不符合现有映射

证据：

- [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:106) `记录列表` path 是 `/app/work-records/list`。
- [App.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/App.tsx:87) 只注册 `/app/work-records`，没有 `/app/work-records/list`。
- [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:105) 使用 `clipboard-list`、`list`、`settings-2`、`book-open`。
- [menu.ts](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/lib/menu.ts:22) 现有映射没有这些 icon key。

影响：

用户从菜单点击记录列表会进入 404，菜单图标也会缺失。也违反刚写入 skill 的“菜单 icon 用现有映射”规则。

修复思路：

- `记录列表` path 改为 `/app/work-records`，或前端增加 `/app/work-records/list` 路由。
- icon 改为现有映射：工作记录 `scroll-text`，记录列表 `file-text`，表单设计 `blocks`，字典管理 `database`。
- 增加菜单 seed 测试，校验 seed path 在前端路由表中存在、icon key 在 `MENU_ICON_MAP` 中存在。

### P2：列表接口无分页，只靠 repository 固定 `limit 200`

证据：

- [WorkRecordController.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordController.java:30) 列表接口只接收 `status`。
- [WorkRecordRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordRepository.java:31) 固定 `limit 200`。

影响：

前端无法分页，用户看不到 200 条以后的数据；API 也不符合 `.agents/skills/aegisops/SKILL.md §6.15.7` 的 page/size/max size 规则。

修复思路：

- 定义 `WorkRecordPageRequest(page, size, status, sort)` 与 `PageResult<T>`。
- Controller 支持 `page=1&size=20`，`size` 最大 100。
- Repository 使用 `limit ? offset ?`，另查 `count(*)`。
- 前端列表接入分页控件。

### P2：导出没有显式最大行数与筛选语义

证据：

- [WorkRecordExportService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordExportService.java:22) 没有 max export rows 参数。
- 当前实际导出复用 repository 的 `limit 200`，这不是产品规则中的“最多 5000/10000 行”，而是列表查询副作用。

影响：

导出上限不可配置、不可解释；后续一旦 repository 分页改造，导出可能失去限制。

修复思路：

- 在 `WorkRecordExportService` 定义 `MAX_EXPORT_ROWS = 5000`。
- Repository 增加专用 export 查询，明确 `limit MAX_EXPORT_ROWS + 1`，超过时返回错误或截断并提示。
- 前端导出当前筛选条件，后端审计记录筛选条件与导出数量。

### P2：保留字段编码未校验

证据：

- [WorkRecordTemplateService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordTemplateService.java:109) 只校验 `fieldCode` 非空。
- 未看到拒绝 `id/title/status/tenant_id/custom_data_json` 等保留字段的逻辑。

影响：

管理员可创建与主表或系统字段冲突的自定义字段，前端渲染、导出、后端校验都会产生歧义。

修复思路：

- 在 service 中增加 `RESERVED_FIELD_CODES`。
- 创建字段时拒绝保留字段；更新时继续禁止修改 `field_code`。
- 加测试覆盖 `title/status/custom_data_json` 等保留值。

### P2：异常类型会被全局处理成 500

证据：

- 工作记录 service 多处抛 `IllegalArgumentException` 和 `SecurityException`。
- [GlobalExceptionHandler.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-web/src/main/java/io/aegisops/web/GlobalExceptionHandler.java:34) 没有专门处理这两类异常，会走 `INTERNAL_ERROR` 500。

影响：

用户输入错误、非法 JSON、无权访问都会表现为服务器内部错误，不利于前端提示，也影响安全语义。

修复思路：

- 对业务校验错误使用 `AppException` 或在 `GlobalExceptionHandler` 增加 `IllegalArgumentException -> 400`。
- 对越权使用 Spring Security 的 `AccessDeniedException`，由安全层返回 403。
- 补 controller 测试覆盖非法请求返回 400、越权返回 403。

### P2：默认字典和默认模板尚未初始化

证据：

- 搜索 `record_type`、`record_priority`、`process_result` 只出现在测试、页面 placeholder 和文档中，未出现在 migration seed。
- [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:101) 只 seed 菜单。

影响：

首次启动后字典和模板为空，不利于演示，也不符合 `.agents/skills/aegisops/SKILL.md §6.15.4` 和材料中的默认数据规则。

修复思路：

- 拆新增 migration：
  - `V0013__init_platform_dictionary_seed.sql`
  - `V0014__init_work_record_default_template.sql`
- 初始化默认字典：`record_type`、`record_status`、`record_priority`、`env_type`、`yes_no`、`process_result`。
- 初始化默认模板和默认字段。

### P3：表单设计页面仍是“表格追加字段”，不是设计器形态

证据：

- [WorkRecordTemplateDesignerPage.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/pages/work-record/WorkRecordTemplateDesignerPage.tsx:134) 文案说明“后续会接入拖拽画布与字段属性面板”。
- 页面没有 `FieldPalette`、`FieldCanvas`、`FieldPropertyPanel` 分层组件。

影响：

这作为骨架可以接受，但不能作为“管理员拖拽制表”验收完成。

修复思路：

- 当前页面文案改为“模板字段配置”，避免误导。
- 下一阶段拆组件：`FieldPalette`、`FieldCanvas`、`FieldPropertyPanel`、`DynamicFormPreview`。
- 不急着引入 Formily；若后续评估 Formily/Designable，应先写 ADR。

## 验证结果

已执行：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
```

结果：通过。`aiops-work-record` 相关测试 25 个通过。

```bash
pnpm --filter @aegisops/console test -- --run
```

结果：通过。15 个测试文件、36 个测试通过。

未执行完整验证：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) bash scripts/ci/verify-local.sh
```

原因：本次重点是规则沉淀和实现审查，完整 CI 耗时更高。合并前仍需运行。

## 建议修复顺序

1. 修复 update JSON 空值覆盖数据的问题。
2. 修复菜单 path 和 icon key。
3. 决定是否立即迁移到 `work_record` schema；若暂缓，必须修改 skill 或写 ADR 说明为什么偏离。
4. 加动态字段后端校验与保留字段校验。
5. 改造列表分页和导出显式上限。
6. 补默认字典与默认模板 seed。
7. 统一异常语义：校验 400，越权 403。
8. 下一阶段再做真正的三栏表单设计器。
