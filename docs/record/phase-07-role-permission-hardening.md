---
title: 工作记录 Phase 07 权限审计与收口
type: design
status: review
phase: work-record
owner: ai
created: 2026-07-07
updated: 2026-07-07
related:
  - docs/record/index.md
---

# Phase 07：角色权限、审计与收口

## 目标

把工作记录第一版收紧到可验收状态：

- 用户管理复用 portal 现有 `features/users`，后续再接真实后端。
- 角色权限新增 `/platform/roles` 页面。
- 后端权限拆分自读、全读、模板管理、导出。
- 模板、字段、字典、记录删除、导出均写审计。
- 补 ArchUnit 边界测试。

## 权限矩阵

```text
platform:dict:read
platform:dict:write
work-record:record:read:self
work-record:record:read:all
work-record:record:write
work-record:record:delete
work-record:template:read
work-record:template:write
work-record:export
role:read
role:write
```

普通用户：

```text
work-record:record:read:self
work-record:record:write
work-record:template:read
platform:dict:read
```

记录管理员：

```text
普通用户权限
work-record:record:read:all
work-record:record:delete
work-record:template:write
work-record:export
platform:dict:write
```

## 后端安全规则

普通用户列表查询强制：

```text
creator_id = currentUserId OR owner_id = currentUserId
```

拥有 `work-record:record:read:all` 才能租户内全量查询。

审计 action：

```text
platform.dict_type.create
platform.dict_type.update
platform.dict_item.create
platform.dict_item.update
work_record.template.create
work_record.template.update
work_record.template.schema.update
work_record.record.create
work_record.record.update
work_record.record.delete
work_record.record.export
```

## 角色页前端文件

```text
web/portal/src/features/roles/index.tsx
web/portal/src/features/roles/api.ts
web/portal/src/features/roles/data/schema.ts
web/portal/src/features/roles/components/roles-table.tsx
web/portal/src/features/roles/components/role-permission-dialog.tsx
web/portal/src/features/roles/hooks/use-roles.ts
```

第一版角色页可以先只做读取与权限勾选保存；不实现复杂组织架构。

## ArchUnit 测试

```java
// apps/aiops-server/src/test/java/io/aegisops/server/WorkRecordBoundaryTest.java
package io.aegisops.server;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class WorkRecordBoundaryTest {
  @Test
  void workRecordControllersDoNotDependOnJdbc() {
    var classes = new ClassFileImporter().importPackages("io.aegisops.workrecord");

    classes()
        .that()
        .haveSimpleNameEndingWith("Controller")
        .should()
        .onlyDependOnClassesThat()
        .resideOutsideOfPackages("org.springframework.jdbc..")
        .check(classes);
  }

  @Test
  void workRecordDomainDoesNotDependOnIncidentOrAlertRepositories() {
    var classes = new ClassFileImporter().importPackages("io.aegisops.workrecord");

    classes()
        .that()
        .resideInAPackage("..workrecord..")
        .should()
        .onlyDependOnClassesThat()
        .resideOutsideOfPackages(
            "io.aegisops.incident..repository..",
            "io.aegisops.alert..repository..",
            "io.aegisops.inspection..repository..")
        .check(classes);
  }
}
```

## 单元测试

```java
// modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordPermissionTest.java
@Test
void normalUserCannotReadOtherUsersRecord() {
  WorkRecordService service = newServiceWithRecord(
      record("record-a", "creator-a", "owner-a"));

  assertThatThrownBy(() -> service.get("tenant-a", user("user-b"), "record-a"))
      .isInstanceOf(AccessDeniedException.class);
}

@Test
void adminCanReadTenantRecords() {
  WorkRecordService service = newServiceWithRecord(
      record("record-a", "creator-a", "owner-a"));

  WorkRecord record = service.get("tenant-a", adminWith("work-record:record:read:all"), "record-a");

  assertThat(record.id()).isEqualTo("record-a");
}
```

```java
// modules/aiops-work-record/src/test/java/io/aegisops/workrecord/WorkRecordAuditTest.java
@Test
void exportWritesAuditLog() {
  RecordingAuditSink auditSink = new RecordingAuditSink();
  WorkRecordExportService service = new WorkRecordExportService(repositoryWithRows(1), auditSink, 5000);

  service.exportCsv("tenant-a", adminWith("work-record:export"), WorkRecordQuery.defaultQuery());

  assertThat(auditSink.actions()).contains("work_record.record.export");
}
```

## 验收

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) bash scripts/ci/backend.sh
cd web/portal && pnpm run lint && pnpm run test && pnpm run build
bash scripts/ci/docs.sh
```

验收标准：

- 普通用户不能查看他人记录。
- 管理员能查看租户内全部记录。
- 导出、删除、模板变更有审计。
- ArchUnit 守住 Controller/JDBC 与跨模块边界。
