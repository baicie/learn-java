---
title: 工作记录模块最终实现审查
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
  - docs/reviews/work-record/2026-07-07-work-record-rule-based-review.md
---

# 工作记录模块最终实现审查

## 结论

当前实现不建议直接收尾。主要阻塞是数据库 schema 与运行时代码不一致、动态字段校验未达到已沉淀规则、前端测试失败。部分上一轮问题已经改善：分页、导出 Authorization header、异常 400/403、记录更新 JSON 保留、字段更新 options 保留、菜单 icon/path、默认字典与默认模板初始化都已有实现动作。

## P0 阻塞问题

### 1. 默认模板初始化访问不存在的表，应用启动可能失败

证据：

- `apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:42` 仍创建 `public.wr_template`。
- `apps/aiops-server/src/main/resources/db/migration/V0013__migrate_work_record_schema.sql:13` 只创建 `work_record` schema，没有迁移表。
- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/DefaultTemplateInitializer.java:66` 查询 `work_record.wr_template`。
- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/DefaultTemplateInitializer.java:81` 写入 `work_record.wr_template`。

风险：Flyway 执行后只有 `public.wr_template`，应用触发 `ApplicationReadyEvent` 时会访问 `work_record.wr_template`，导致默认模板 seed 报错。与此同时，Repository 仍访问无 schema 前缀的 `wr_*` 表，运行时路径也不一致。

修复思路：

1. 选择一次性收敛到独立 schema：新增 migration 把 `public.wr_template`、`public.wr_template_field`、`public.wr_record` 迁移到 `work_record`，并重建/确认索引与外键。
2. 所有 Repository SQL 统一改为 `work_record.wr_*`。
3. 更新 `jooq-codegen.xml` 与相关 jOOQ 断言测试，明确生成 schema 后的包名和表名。
4. 补一个 Spring/Flyway 集成测试，至少断言 `work_record.wr_template` 存在且默认模板初始化不抛错。

## P1 高优先级问题

### 2. 动态字段校验没有拒绝未知字段，违反“字段必须属于模板”

证据：

- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldValidator.java:57` 只建立模板字段 map。
- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldValidator.java:73` 只遍历已定义字段。
- 没有遍历 `custom_data_json` 的输入 key 并拒绝不在模板内的字段。

风险：客户端可以写入任意 JSON key，例如 `{"shadow_status":"approved"}`，后续列表、导出、统计或字段升级时会出现不可解释的脏数据。

修复思路：

1. 遍历输入 JSON 对象字段名，跳过明确允许的内部键后，要求每个 key 都存在于模板字段定义。
2. 禁止 `__builtin__` 这类绕开主表字段边界的临时容器，除非先写入设计规则。
3. 增加单测：未知字段被拒绝、禁用字段被拒绝、空对象只在无必填字段时通过。

### 3. select / multi_select 只校验类型，不校验合法值

证据：

- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldValidator.java:105` 到 `115` 只检查 select 是字符串、multi_select 是数组。
- 未解析 `options_json`，也未查询 `option_source=dict` 对应字典项。

风险：记录可以保存模板未定义的枚举值，破坏“记录存 value，展示再查 label”的前提。禁用字典项历史展示也无法被可靠区分。

修复思路：

1. 对 `option_source=static` 解析 `options_json`，建立允许的 value 集合。
2. 对 `option_source=dict` 注入字典查询边界，校验启用项；历史展示可查禁用项，但新写入应拒绝禁用项。
3. 增加单测：select 非法值拒绝、multi_select 中任一非法值拒绝、合法值通过。

### 4. 日期 / 日期时间只校验字符串，不校验格式

证据：

- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldValidator.java:116` 到 `122` 仅要求 textual。

风险：`date` 可保存 `"tomorrow"`，`datetime` 可保存 `"not-a-date"`，与规则中的 `YYYY-MM-DD` 和 ISO string 不一致。

修复思路：

1. `date` 使用 `LocalDate.parse(value)`。
2. `datetime` 使用 `OffsetDateTime.parse(value)`，若前端使用 `datetime-local`，需要在提交前转换为带 offset 的 ISO 字符串，或后端明确接受 `LocalDateTime` 并补规则。
3. 补前后端测试覆盖格式错误。

### 5. 默认模板 seed 使用了保留字段编码

证据：

- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/DefaultTemplateInitializer.java:132` 到 `142` 新增字段编码 `record_time`。
- `modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldValidator.java:22` 到 `24` 已把 `record_time` 列为保留字段。

风险：系统默认数据自己违反字段规则，后续一旦统一走校验或迁移脚本，会出现默认模板不可编辑、不可复制或不可导入的问题。

修复思路：把该字段改名为业务含义更明确的自定义字段，例如 `inspection_time`；默认模板初始化也应复用同一套字段编码校验。

### 6. 前端测试失败，当前 console 不能通过验证

证据：

- 执行 `pnpm --filter @aegisops/console test -- --run` 失败。
- 失败用例：`web/console/src/pages/work-record/WorkRecordTemplateDesignerPage.test.tsx:31` 仍期待文本 `表单设计`。
- 当前页面实际标题在 `web/console/src/pages/work-record/WorkRecordTemplateDesignerPage.tsx:64` 为 `模板字段配置`。

修复思路：二选一即可：若产品名改为“模板字段配置”，更新测试断言；若菜单和需求仍叫“表单设计”，恢复页面标题并保持测试不变。

## P2 中优先级问题

### 7. 表单设计器仍未达到第一版规则中的“排序、属性编辑、禁用、预览”

证据：

- `web/console/src/pages/work-record/WorkRecordTemplateDesignerPage.tsx:167` 到 `198` 只有按模板新增字段的行内表单。
- 页面缺少字段排序、属性编辑、禁用字段、预览表单入口。

风险：这与“不要大而全”不冲突，但仍低于 Skill 中第一版设计器最低功能线。当前页面更准确地说是模板字段追加页，不是设计器。

修复思路：拆出 `FieldPalette`、`FieldCanvas`、`FieldPropertyPanel`、`DynamicFormPreview`，第一版只做列表排序和右侧属性编辑，不引入 Formily / Designable。

### 8. 放宽全局 Checkstyle 参数上限需要单独确认

证据：

- `infra/checkstyle/checkstyle.xml` 把 `ParameterNumber.max` 从 `5` 改为 `6`。

风险：这是全仓库代码标准变化，不是工作记录模块局部实现。若只是为了少数 DTO 或方法过线，容易降低长期约束。

修复思路：优先通过 request 对象、局部 helper 或拆分方法解决；若确实要放宽，需要在 review/ADR 中说明原因和影响面。

## 已改善项

- `WorkRecordController` 已支持 `page` / `size`，`PageResult` 默认 page=1、size=20、max=100。
- 非 `read:all` 用户列表与导出会限制到 owner/creator。
- CSV 下载使用 `downloadFile`，通过 Authorization header 携带 token。
- `GlobalExceptionHandler` 已把 `IllegalArgumentException` 映射为 400，把 `SecurityException` / `AccessDeniedException` 映射为 403。
- 更新记录时省略 JSON 不再覆盖为 `{}`。
- 更新字段时省略 `optionsJson` 不再覆盖为 `[]`。
- 菜单 path 和 icon 已基本对齐现有路由与 icon map。

## 验证结果

- `pnpm --filter @aegisops/console test -- --run`：失败，15 个测试文件中 1 个失败，原因是表单设计页测试断言旧文案。
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test`：失败于 `aiops-audit` 的 Mockito inline 初始化。当前机器没有 JDK 21，`/usr/libexec/java_home -v 21` 实际返回 JDK 25。
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apps/aiops-server -am test -DskipITs`：同样失败于 JDK 25 下 Mockito inline 自附加问题，尚未跑到 server / work-record 测试。
- `bash scripts/ci/verify-local.sh`：未执行；在前端测试失败和 JDK 21 缺失前提下，完整验证没有收敛意义。

## 收尾建议

先修 P0 和 P1，再进入最终验收：

1. 统一 `work_record` schema 与 Repository SQL，并补 migration 集成测试。
2. 补完整动态字段校验：未知字段、枚举合法值、日期格式。
3. 修正默认模板保留字段冲突。
4. 修复前端测试断言或页面标题。
5. 准备真实 JDK 21 环境后重跑 work-record、server、console 与 `verify-local.sh`。
