# Phase4.6 jOOQ Codegen

## 目标

Phase4.6 将 Phase4.5 的 no-codegen jOOQ 过渡到 generated Tables。

## 当前范围

- aiops-persistence 增加 jOOQ codegen
- 从 Flyway migration SQL 生成 schema
- 生成包名：io.aegisops.persistence.jooq
- 生成目录：target/generated-sources/jooq
- aiops-evidence 迁移到 generated Tables
- aiops-ai-client / aiops-rca 暂时保留 Phase4.5 AegisTables

## 不做

- 不删除 AegisTables
- 不一次性迁移所有 Repository
- 不修改 Flyway schema
- 不修改 API contract
- 不修改 Python Agent

## 关键技术说明

### DDLDatabase scripts 配置

`DDLDatabase` 的 `scripts` 属性接受**目录路径**，DDLDatabase 会扫描该目录下所有 `*.sql` 文件并按 `sort` 配置排序。**不接受 glob 表达式**（如 `V*.sql` 或 `*.sql`），只会产生空输出。

```xml
<!-- 正确：传目录 -->
<property>
  <key>scripts</key>
  <value>../../apps/aiops-server/src/main/resources/db/migration</value>
</property>

<!-- 错误：glob 表达式，只产生空输出 -->
<property>
  <key>scripts</key>
  <value>../../apps/aiops-server/src/main/resources/db/migration/*.sql</value>
</property>
```

### forcedTypes 与 JSONB

Flyway migration 中的 `jsonb` 列类型在 DDLDatabase 解析后映射为 `SQLDataType.JSON`，因此 `<includeTypes>jsonb</includeTypes>` 不会匹配。

正确做法是使用 `<includeTypes>JSON</includeTypes>` 匹配已解析的类型：

```xml
<forcedTypes>
  <forcedType>
    <userType>org.jooq.JSONB</userType>
    <includeTypes>JSON</includeTypes>
  </forcedType>
</forcedTypes>
```

生成的字段类型为 `TableField<R, JSONB>`，底层仍是 `SQLDataType.JSON`，运行时与 PostgreSQL jsonb 兼容。

## 后续

Phase4.7 再迁移 aiops-ai-client 与 aiops-rca 到 generated Tables，迁移完成后删除 AegisTables。
