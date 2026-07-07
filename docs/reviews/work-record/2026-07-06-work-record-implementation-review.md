---
title: 工作记录模块当前实现审查
type: review
status: draft
phase: work-record
owner: ai
created: 2026-07-06
updated: 2026-07-06
related:
  - docs/record/index.md
  - docs/record/work-record-phases-design-code.md
---

# 工作记录模块当前实现审查

审查范围：当前工作区未提交改动，重点覆盖 `aiops-work-record`、`aiops-platform` 字典能力、Flyway migration、前端工作记录页面与菜单接入。

## 结论

当前实现已经能通过主要后端与前端测试，但还不建议直接合并。主要问题集中在：已发布 migration 被重命名、导出鉴权方式错误且泄露 token、普通用户可查询全量工作记录、工作记录/字典/模板的编辑闭环不完整、敏感操作缺少审计。

## 发现

### P1：已存在的 Flyway migration 被重命名，可能导致已部署环境启动校验失败

证据：

- `git status --short` 显示删除：
  - `apps/aiops-server/src/main/resources/db/migration/V0008__platform_navigation_workspace.sql`
  - `apps/aiops-server/src/main/resources/db/migration/V0009__alert_ingest_rules.sql`
  - `apps/aiops-server/src/main/resources/db/migration/V0010__evidence_collection_task.sql`
- 同时新增：
  - `V0008__init_platform_navigation_workspace.sql`
  - `V0009__init_alert_ingest_rules.sql`
  - `V0010__init_evidence_collection_task.sql`

影响：

Flyway migration 一旦在环境中执行过，就不应改名或改内容。即使版本号相同，描述变化也可能触发 validate 风险；更重要的是这会让审计历史和部署排障变得混乱。

修复思路：

- 恢复原始 `V0008__platform_navigation_workspace.sql`、`V0009__alert_ingest_rules.sql`、`V0010__evidence_collection_task.sql` 文件名。
- 保留新增工作记录 migration 为 `V0012__init_work_record.sql`。
- 如确实需要修正旧 migration 行为，新增 `V0013__fix_*.sql`，不要修改已存在版本。

### P1：CSV 导出把 JWT 放进 URL，但后端只识别 `Authorization: Bearer`，导出会失败且泄露 token

证据：

- [WorkRecordListPage.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/pages/work-record/WorkRecordListPage.tsx:30) 将 token 拼成 `?access_token=...`。
- [JwtAuthenticationFilter.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-security/src/main/java/io/aegisops/security/JwtAuthenticationFilter.java:28) 只读取 `Authorization` header。
- [WorkRecordExportController.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordExportController.java:24) 使用 `@PreAuthorize("hasAuthority('work-record:export')")`，没有处理 query token。

影响：

用户点击“导出 CSV”大概率会被 401/403 拦截；同时 token 出现在 URL 中，会进入浏览器历史、代理日志、服务端访问日志，属于安全风险。

修复思路：

- 前端改成 `fetch(WORK_RECORD_EXPORT_URL, { headers: { Authorization: Bearer token } })`，拿到 blob 后用 `URL.createObjectURL` 触发下载。
- 或后端提供短期一次性导出 token，但不要复用登录 JWT 作为 query 参数。
- 补一个前端测试覆盖导出请求 header，补一个后端 controller/security 测试覆盖导出鉴权。

### P1：普通用户可查询全租户工作记录，缺少“我的记录/全部记录”的权限边界

证据：

- [WorkRecordController.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordController.java:29) 列表接口只要求 `work-record:read`。
- [WorkRecordRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordRepository.java:20) 当 `ownerId` 为空时返回当前租户全部记录。
- [UserPrincipal.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-security/src/main/java/io/aegisops/security/UserPrincipal.java:64) operator 也拥有 `work-record:read`。

影响：

只要拥有 `work-record:read`，用户就能不传 `ownerId` 查看租户内所有记录。这不符合 `docs/record/index.md` 中“管理员看全部，普通用户看自己的”的第一版权限边界。

修复思路：

- 拆权限：`work-record:record:read:self` 与 `work-record:record:read:all`，或沿用简化命名但增加 `work-record:read:all`。
- Controller 接收 `@AuthenticationPrincipal UserPrincipal user`，非 all 权限时强制 `ownerId/user.id()` 或 `creatorId/user.id()`。
- Repository 增加“我的记录”查询语义，避免由前端传入 ownerId 决定数据范围。
- 补 controller/service 测试：operator 只能看到自己的记录，admin 可查全部。

### P1：编辑闭环未实现，当前“编辑工作记录”只是只读占位

证据：

- [WorkRecordEditPage.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/pages/work-record/WorkRecordEditPage.tsx:56) 编辑态标题为“记录内容（只读占位）”。
- [WorkRecordController.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordController.java:41) 只有 `POST` 创建和 `DELETE` 删除，没有 `PUT/PATCH` 更新。
- 字典与模板 controller 目前也只有 list/create，缺少 update/delete。

影响：

`docs/record/index.md` 要求“管理员维护字典、表单设计、用户填写/编辑记录、管理员筛选导出”。当前实现只能新增，不能真正维护，业务闭环不完整。

修复思路：

- 增加：
  - `PUT /api/platform/dictionaries/{dictCode}`
  - `PUT /api/platform/dictionaries/{dictCode}/items/{itemId}`
  - `PUT /api/work-record/templates/{templateId}`
  - `PUT /api/work-record/templates/{templateId}/fields/{fieldId}`
  - `PUT /api/work-record/records/{recordId}`
- 前端编辑页使用模板字段动态渲染 `customDataJson`，不要只展示 JSON。
- 删除能力优先做软删除/禁用，避免误删模板导致历史记录不可解释。

### P2：敏感操作缺少审计记录

证据：

- 字典新增、模板新增、字段新增、记录创建/删除、导出均未调用 `AuditService`。
- [WorkRecordService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordService.java:32) 创建记录只写业务表。
- [WorkRecordExportService.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordExportService.java:17) 导出只读数据并生成 CSV。

影响：

项目规范要求安全敏感操作必须生成审计日志。工作记录虽然不是自动化执行，但包含运维记录沉淀、导出和管理配置，至少导出、删除、模板/字段变更应审计。

修复思路：

- `aiops-work-record` 依赖 `aiops-audit` 或定义轻量审计端口，由 server 装配实现。
- 审计动作建议：
  - `work_record.record.create`
  - `work_record.record.update`
  - `work_record.record.delete`
  - `work_record.record.export`
  - `work_record.template.create/update/delete`
  - `work_record.field.create/update/delete`
  - `platform.dict_type.create/update/delete`
  - `platform.dict_item.create/update/delete`
- 测试校验关键 service 调用审计端口。

### P2：JSON 字段没有应用层校验，非法 JSON 会下沉成数据库异常

证据：

- [DictionaryRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-platform/src/main/java/io/aegisops/platform/dictionary/DictionaryRepository.java:107) 直接 `?::jsonb` 写入 `extraJson`。
- [WorkRecordRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordRepository.java:73) 直接写入 `builtinDataJson/customDataJson`。
- [WorkRecordFieldRepository.java](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-work-record/src/main/java/io/aegisops/workrecord/WorkRecordFieldRepository.java:54) 直接写入 `optionsJson`。

影响：

非法 JSON 会变成 JDBC/数据库异常，通常被全局异常处理成 `INTERNAL_ERROR`，前端也难以给出字段级提示。

修复思路：

- 在 service 层用 Jackson `ObjectMapper.readTree` 校验 JSON。
- `optionsJson` 必须校验为数组，`builtinDataJson/customDataJson/extraJson/schemaJson` 必须校验为对象。
- 对校验失败抛出明确业务异常或 `IllegalArgumentException`，并在全局异常处理里返回 400。
- 补充单元测试覆盖非法 JSON、数组/对象类型错误。

### P2：jOOQ codegen includes 写的是错误表名

证据：

- [jooq-codegen.xml](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/modules/aiops-persistence/src/main/resources/jooq-codegen.xml:70) 新增 `template | template_field | record`。
- 实际表名是 [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:42) 中的 `wr_template`、`wr_template_field`、`wr_record`。

影响：

当前工作记录模块使用 `JdbcTemplate`，所以暂时不炸；但生成模型不完整，后续如果切 jOOQ 或做 schema 测试会踩坑。

修复思路：

- 将 includes 改为：
  - `wr_template | wr_template_field | wr_record`
  - 保留 `platform_dict_type | platform_dict_item`
- 增加持久层测试断言这些表生成成功。

### P2：菜单父节点指向不存在的路由

证据：

- [V0012__init_work_record.sql](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/apps/aiops-server/src/main/resources/db/migration/V0012__init_work_record.sql:105) 父菜单路径为 `/app/work-records/overview`。
- [App.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/App.tsx:87) 只注册了 `/app/work-records`、`/app/work-records/create`、`/app/work-records/:recordId/edit`、`/app/work-records/designer`。

影响：

如果侧边栏允许点击父菜单，用户会进入 404。即使父菜单只展开不跳转，也会增加菜单行为不一致。

修复思路：

- 父菜单路径改为 `/app/work-records`。
- 或新增 `/app/work-records/overview` 路由并明确它是工作记录概览页。
- 菜单测试加一条：所有 seed 菜单 path 必须在前端 route 中存在。

### P2：构建产物处于 untracked 状态，容易被误提交

证据：

- `git status --short` 显示 `modules/aiops-work-record/target/**` 为未跟踪文件。

影响：

`target/` 产物不应进入版本控制；一旦误提交会污染 diff，且容易产生 JDK 版本 class file 冲突。

修复思路：

- 确认 `.gitignore` 覆盖 `target/`，必要时补 `**/target/`。
- 删除未跟踪构建产物：`git clean -fd modules/aiops-work-record/target`。执行前确认没有需要保留的手工文件。

### P3：字典管理页面复用 `features/work-record/api.ts`，边界不够清晰

证据：

- [DictionaryPage.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/pages/platform/DictionaryPage.tsx:22) 从 `../../features/work-record/api` 引入字典 API。

影响：

字典是平台能力，不是工作记录私有能力。放在 work-record feature 下会让后续其他模块引用字典时依赖方向变乱。

修复思路：

- 拆出 `web/console/src/features/platform-dictionary/api.ts` 和 `types.ts`。
- 工作记录需要字典项时通过平台字典 feature 复用。

### P3：表单设计页当前不是拖拽制表，只是表格内追加字段

证据：

- [WorkRecordTemplateDesignerPage.tsx](/Users/liuzhiwei/Desktop/workspace/git-code/ai-ops/web/console/src/pages/work-record/WorkRecordTemplateDesignerPage.tsx:134) 页面说明“后续会接入拖拽画布与字段属性面板”。

影响：

这与 `docs/record/index.md` 中“管理员拖拽设计工作记录表”的目标还有差距。作为 Phase 早期骨架可以接受，但不应标记为完整实现。

修复思路：

- 当前阶段文案改为“字段配置”或“模板字段维护”，避免用户误解。
- 后续再实现三栏设计器：字段组件区、画布、属性面板。

## 验证结果

已执行：

```bash
pnpm --filter @aegisops/console test -- --run
```

结果：通过，14 个测试文件、35 个测试通过。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-work-record -am test
```

结果：通过，`aiops-work-record` 相关测试 15 个通过。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apps/aiops-server -am test -DskipITs
```

结果：通过，完整 server reactor build success。

未通过/需说明：

```bash
mvn -pl modules/aiops-work-record -am test
mvn -pl modules/aiops-platform -am test
```

在未指定 Java 21 时失败，原因是当前默认运行时只能识别 Java 17 class file，而工作区已有 Java 21 编译产物。按项目说明指定 Java 21 后可通过或在完整 server reactor 中通过。

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl modules/aiops-platform -am test
```

单独执行时在 `aiops-persistence` 的 surefire fork 启动阶段异常退出；完整 `apps/aiops-server` reactor 随后通过，判断更像本地 JDK/Surefire fork 偶发或并发执行干扰，不作为本实现的功能失败，但建议后续单独复跑确认。

## 建议修复顺序

1. 恢复 V0008/V0009/V0010 migration 原文件名。
2. 修复导出鉴权：移除 URL token，改 header 下载。
3. 修复工作记录列表权限边界，区分“我的”和“全部”。
4. 补齐 update/delete/disable API 与前端编辑闭环。
5. 接入审计与 JSON 校验。
6. 修正 jOOQ includes 与菜单父路由。
7. 清理 `target/` 未跟踪产物并完善 `.gitignore`。
