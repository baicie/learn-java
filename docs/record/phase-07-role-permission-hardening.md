---
title: 工作记录 Phase 07 权限审计与交付收口
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-08
related:
  - docs/record/index.md
  - docs/record/phase-06-record-list-export.md
---

# Phase 07：角色权限、审计、质量门禁与交付收口

## 1. 阶段目标

Phase 07 不再新增大功能，而是把 Phase 01 到 Phase 06 的工作记录能力收紧到“可以进入 MVP 主线”的质量标准。

本阶段必须完成：

```text
1. 明确工作记录、字典、模板、导出的权限矩阵。
2. 将权限接入后端鉴权与前端可见性控制。
3. 补齐模板、字段、字典、记录、导出的审计日志。
4. 补齐多租户隔离与普通用户数据范围测试。
5. 补齐架构边界测试，防止工作记录模块越界依赖 Incident / Alert / Inspection。
6. 补齐 OpenAPI、docs/api 与 docs/record 的交付说明。
7. 明确当前 portal 构建阻塞是否来自模板历史问题，避免把已有技术债误判成本模块回归。
8. 输出第一版上线检查清单。
```

本阶段不做：

```text
1. 不重做完整 IAM 系统。
2. 不引入组织架构、数据权限表达式引擎。
3. 不实现动态菜单后端化。
4. 不实现审批流。
5. 不实现异步导出中心。
```

## 2. 权限模型

### 2.1 权限命名

权限 code 必须稳定、可读、可审计：

```text
platform:dict:read
platform:dict:write

platform:calendar:read
platform:calendar:write
platform:calendar:import

work-record:template:read
work-record:template:write
work-record:template:delete

work-record:record:read:self
work-record:record:read:all
work-record:record:write
work-record:record:delete
work-record:record:export

role:read
role:write
```

第一版不设计字段级权限。原因：

```text
1. 工作记录字段由管理员配置，字段级 ACL 会显著增加模板、运行态、列表、导出复杂度。
2. 当前 MVP 重点是记录闭环，不是复杂数据治理。
3. 如果未来出现敏感字段需求，应单独设计 field.sensitive 与脱敏策略。
```

### 2.2 角色建议

普通用户：

```text
platform:dict:read
platform:calendar:read
work-record:template:read
work-record:record:read:self
work-record:record:write
```

记录管理员：

```text
普通用户全部权限
platform:dict:write
platform:calendar:write
platform:calendar:import
work-record:template:write
work-record:template:delete
work-record:record:read:all
work-record:record:delete
work-record:record:export
```

系统管理员：

```text
记录管理员全部权限
role:read
role:write
```

### 2.3 操作权限矩阵

| 操作           | 权限                           | 数据范围                       |
| -------------- | ------------------------------ | ------------------------------ |
| 查看字典       | `platform:dict:read`           | 当前租户                       |
| 管理字典       | `platform:dict:write`          | 当前租户                       |
| 查看工作日历   | `platform:calendar:read`       | 当前租户                       |
| 管理工作日历   | `platform:calendar:write`      | 当前租户                       |
| 导入工作日历   | `platform:calendar:import`     | 当前租户                       |
| 查看模板       | `work-record:template:read`    | 当前租户                       |
| 新建/编辑模板  | `work-record:template:write`   | 当前租户                       |
| 删除模板       | `work-record:template:delete`  | 当前租户，且无已提交记录时允许 |
| 新建记录       | `work-record:record:write`     | 当前租户，可用模板             |
| 编辑自己的草稿 | `work-record:record:write`     | creator/owner 为本人           |
| 查看自己的记录 | `work-record:record:read:self` | creator/owner 为本人           |
| 查看全部记录   | `work-record:record:read:all`  | 当前租户                       |
| 删除记录       | `work-record:record:delete`    | 当前租户                       |
| 导出记录       | `work-record:record:export`    | 当前权限可见范围               |
| 查看角色       | `role:read`                    | 当前租户                       |
| 编辑角色权限   | `role:write`                   | 当前租户                       |

## 3. 后端安全设计

### 3.1 鉴权入口

Controller 层只声明权限，不写复杂判断：

```text
GET    /api/work-record/templates       work-record:template:read
POST   /api/work-record/templates       work-record:template:write
PUT    /api/work-record/templates/{id}  work-record:template:write
DELETE /api/work-record/templates/{id}  work-record:template:delete

GET    /api/work-record/records         work-record:record:read:self 或 read:all
GET    /api/work-record/records/{id}    work-record:record:read:self 或 read:all
POST   /api/work-record/records         work-record:record:write
PUT    /api/work-record/records/{id}    work-record:record:write
DELETE /api/work-record/records/{id}    work-record:record:delete
POST   /api/work-record/records/export  work-record:record:export
```

Service 层负责数据范围：

```text
1. tenantId 来自认证上下文，不接受前端传入覆盖。
2. read:all 可以看当前租户全部记录。
3. read:self 只能看 creator_id=currentUserId 或 owner_id=currentUserId。
4. export 权限只代表允许导出，导出数据仍受 read:self/read:all 约束。
5. delete 是逻辑删除或状态删除，不做物理删除。
```

### 3.2 多租户隔离

所有 Repository 方法必须显式携带 `tenantId`：

```text
findTemplate(tenantId, templateId)
findRecord(tenantId, recordId)
listRecords(tenantId, query)
updateRecord(tenantId, recordId, patch)
deleteRecord(tenantId, recordId)
```

禁止：

```text
1. 只按 id 查询模板、字段、记录。
2. 从 request body 接受 tenantId。
3. 后端日志打印完整 customData 中的敏感值。
4. 导出跨租户数据。
```

### 3.3 删除策略

模板删除：

```text
1. 已被记录引用的模板不允许物理删除。
2. 可以 disabled，使其不可新增记录。
3. 历史记录详情继续使用 snapshot/schemaJson 展示。
```

记录删除：

```text
1. 第一版采用逻辑删除。
2. 列表默认不展示 deleted=true。
3. 删除必须写审计。
4. 删除后不允许普通用户通过详情接口读取。
```

字典删除：

```text
1. 字典类型不建议物理删除。
2. 字典项使用 enabled=false 停用。
3. 历史记录展示时如果 value 已停用，仍展示历史 label 或 value。
```

## 4. 审计设计

### 4.1 审计动作

字典：

```text
platform.dict_type.create
platform.dict_type.update
platform.dict_type.disable
platform.dict_item.create
platform.dict_item.update
platform.dict_item.disable
```

工作日历：

```text
platform.calendar.create
platform.calendar.update
platform.calendar.disable
platform.calendar_day.update
platform.calendar_day.import
```

模板：

```text
work_record.template.create
work_record.template.update
work_record.template.disable
work_record.template.schema.update
work_record.template.field.create
work_record.template.field.update
work_record.template.field.remove
```

记录：

```text
work_record.record.create
work_record.record.update
work_record.record.submit
work_record.record.delete
work_record.record.export
```

角色权限：

```text
platform.role.permission.update
```

### 4.2 审计字段

审计日志至少包含：

```text
tenantId
actorId
actorName
action
resourceType
resourceId
resourceName
requestId
clientIp
userAgent
occurredAt
summary
beforeSnapshot
afterSnapshot
```

`beforeSnapshot` 和 `afterSnapshot` 只记录关键字段，不记录完整大 JSON：

```text
模板：name, enabled, schemaVersion, fieldCodes
字段：fieldCode, label, fieldType, required, filterable, listVisible
记录：title, status, templateId, ownerId, changedFieldCodes
导出：templateId, filtersDigest, rowCount, columns, format
```

### 4.3 审计脱敏

审计中禁止直接记录：

```text
完整 customDataJson
完整 schemaJson
完整导出内容
完整 request header
token / cookie / password / secret
```

如需排查问题，使用：

```text
schemaHash
filtersDigest
changedFieldCodes
rowCount
```

## 5. 前端角色与权限页面

第一版角色页只是工作记录模块权限管理的最小可用界面，不取代完整用户中心。

路径：

```text
/platform/roles
```

文件落点：

```text
web/portal/src/routes/_authenticated/platform/roles/index.tsx
web/portal/src/features/roles/index.tsx
web/portal/src/features/roles/api/roles-api.ts
web/portal/src/features/roles/data/role-schema.ts
web/portal/src/features/roles/hooks/use-roles.ts
web/portal/src/features/roles/components/roles-table.tsx
web/portal/src/features/roles/components/role-permission-dialog.tsx
web/portal/src/features/roles/components/permission-group.tsx
```

页面能力：

```text
1. 查看角色列表。
2. 查看角色已有权限。
3. 按权限组勾选权限。
4. 保存角色权限。
5. 保存成功后刷新角色列表。
```

权限组：

```text
平台字典
工作日历
工作记录模板
工作记录数据
导出
角色权限
```

前端权限控制原则：

```text
1. 前端隐藏无权限按钮只是体验优化。
2. 后端鉴权永远是最终裁决。
3. 菜单第一版仍保留静态 sidebar-data.ts，后续再规划动态菜单。
4. 没有 role:read 时，/platform/roles 页面显示 403 状态。
```

## 6. 架构边界收口

工作记录模块允许依赖：

```text
shared/common
tenant/context
auth/current-user
audit/audit-sink
dictionary read model
```

工作记录模块禁止依赖：

```text
incident repository
alert repository
inspection repository
automation runner
ai diagnosis service
zabbix adapter
```

原因：

```text
工作记录是运维过程中的通用记录能力，可以被 Incident / Inspection 使用，
但不能反过来依赖具体业务域，否则后续会被故障、巡检、自动化等模块拖成混杂模块。
```

ArchUnit 规则：

```java
@Test
void workRecordControllersDoNotDependOnJdbc() {
  var imported = new ClassFileImporter().importPackages("io.aegisops.workrecord");

  classes()
      .that()
      .haveSimpleNameEndingWith("Controller")
      .should()
      .onlyDependOnClassesThat()
      .resideOutsideOfPackages("org.springframework.jdbc..")
      .check(imported);
}

@Test
void workRecordDoesNotDependOnIncidentAlertInspectionRepositories() {
  var imported = new ClassFileImporter().importPackages("io.aegisops.workrecord");

  classes()
      .that()
      .resideInAPackage("..workrecord..")
      .should()
      .onlyDependOnClassesThat()
      .resideOutsideOfPackages(
          "io.aegisops.incident..repository..",
          "io.aegisops.alert..repository..",
          "io.aegisops.inspection..repository..")
      .check(imported);
}
```

## 7. OpenAPI 与文档

本阶段必须同步：

```text
1. OpenAPI：字典、模板、记录、列表、导出、角色权限接口。
2. docs/api：说明权限、错误码、导出限制。
3. docs/record：记录实际落地状态、已知限制、后续增强项。
4. Flyway migration：新增表或字段必须有 migration。
5. docs/INDEX.md：不手工维护，如需更新走文档脚本。
```

错误码建议：

```text
WORK_RECORD_TEMPLATE_NOT_FOUND
WORK_RECORD_TEMPLATE_DISABLED
WORK_RECORD_FIELD_NOT_FOUND
WORK_RECORD_FIELD_NOT_FILTERABLE
WORK_RECORD_FILTER_OPERATOR_UNSUPPORTED
WORK_RECORD_EXPORT_LIMIT_EXCEEDED
WORK_RECORD_PERMISSION_DENIED
WORK_RECORD_TENANT_SCOPE_REQUIRED
```

## 8. 测试策略

### 8.1 权限测试

```text
WorkRecordPermissionTest
  - normalUserCannotReadOtherUsersRecord
  - normalUserCanReadOwnCreatedRecord
  - normalUserCanReadOwnedRecord
  - adminCanReadTenantRecords
  - exportRequiresExportPermission
  - exportRespectsSelfReadScope
  - deleteRequiresDeletePermission
```

### 8.2 多租户测试

```text
WorkRecordTenantIsolationTest
  - cannotReadRecordFromAnotherTenant
  - cannotUpdateTemplateFromAnotherTenant
  - cannotUseDictionaryFromAnotherTenant
  - listNeverReturnsAnotherTenantRecords
  - exportNeverReturnsAnotherTenantRecords
```

### 8.3 审计测试

```text
WorkRecordAuditTest
  - templateCreateWritesAudit
  - templateSchemaUpdateWritesChangedFieldCodes
  - recordCreateWritesAudit
  - recordUpdateWritesChangedFieldCodes
  - recordDeleteWritesAudit
  - exportWritesAuditWithRowCount
  - auditDoesNotStoreFullCustomData
```

### 8.4 前端权限测试

```text
roles-page.test.tsx
  - rendersPermissionGroups
  - togglesPermission
  - savesRolePermissions
  - showsForbiddenWithoutRoleRead

record-actions.test.tsx
  - hidesExportButtonWithoutPermission
  - hidesDeleteActionWithoutPermission
  - stillHandlesBackendForbidden
```

### 8.5 架构测试

```text
WorkRecordBoundaryTest
  - controllersDoNotDependOnJdbc
  - domainDoesNotDependOnIncidentAlertInspectionRepositories
  - dictionaryDoesNotDependOnWorkRecord
  - repositoryMethodsRequireTenantId
```

## 9. 交付门禁

后端：

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -pl apps/aiops-server -am test
```

前端：

```bash
pnpm --dir web/portal run lint
pnpm --dir web/portal run test
pnpm --dir web/portal run build
```

文档：

```bash
bash scripts/ci/docs.sh
git diff --check
```

如果 `web/portal` 构建失败，交付说明必须区分：

```text
1. 本模块新增代码导致的失败。
2. portal 模板历史遗留失败。
3. 本机环境缺失导致的失败，例如 Playwright 浏览器未安装。
```

已知历史类问题不能静默吞掉，必须记录命令、失败摘要与影响判断。

## 10. 上线检查清单

```text
1. 所有工作记录表、字段、索引 migration 已合入。
2. migration 可在空库和已有开发库上执行。
3. 权限 code 已写入默认角色或初始化脚本。
4. 普通用户、记录管理员、系统管理员三类账号验收通过。
5. 模板新增、编辑、停用有审计。
6. 字典新增、编辑、停用有审计。
7. 记录新增、编辑、删除有审计。
8. 导出有权限、有行数上限、有审计、有 CSV 注入防护。
9. 列表筛选不能绕过 filterable=false。
10. 所有查询包含 tenantId。
11. OpenAPI 与 docs/api 已同步。
12. docs/record 各 Phase 状态与实际实现一致。
13. 前端所有用户可见关键文案进入 TS locale 文件。
14. 交付说明列出第一版不支持项：异步导出、字段级权限、动态菜单、审批流。
```

## 11. 第一版完成定义

满足以下条件才认为工作记录第一版完成：

```text
管理员可以维护字典。
管理员可以维护模板字段与表单 schema。
用户可以基于模板填写记录。
用户可以查看自己的记录。
管理员可以查看租户内记录。
管理员可以按固定字段和 filterable 自定义字段筛选。
管理员可以导出当前筛选结果。
关键动作有审计。
普通用户无法越权读取他人记录。
跨租户数据无法读取、更新、导出。
后端边界测试防止模块依赖倒置。
前端 i18n、路由、菜单、页面状态符合 portal 现有风格。
```
