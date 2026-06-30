---
title: Phase4.6：jOOQ Codegen 正式化
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---

# Phase4.6：jOOQ Codegen 正式化

基于当前 `mvp` 最新实现，Phase4.6 建议做 **jOOQ generated Tables 接入**，让 Phase4.5 的 no-codegen `AegisTables.str(table, "column")` 过渡到真正的编译期表结构。

当前状态是：

```txt id="pwp2m8"
Phase4.5 已完成：
  modules/aiops-persistence
  AegisJooq
  AegisTables
  JdbcAiRepository -> DSLContext
  JdbcEvidenceRepository -> DSLContext
  JdbcRcaRepository -> DSLContext

Phase4.6 要做：
  Flyway SQL -> jOOQ generated sources
  生成 io.aegisops.persistence.jooq.tables.*
  先迁移 Evidence Repository 到 generated Tables
  保留 AegisTables 作为兼容层
```

我建议 **Phase4.6 不一次性迁移 AI/RCA/Evidence 三个大 Repository**，而是先把 codegen 链路跑通，并迁移 `aiops-evidence` 这个相对小的 Repository。原因是：`JdbcAiRepository` 已经很大，直接全量替换容易引入不可控问题；`JdbcEvidenceRepository` 小、SQL 覆盖 metrics/logs/changes evidence 的关键路径，适合作为第一条 generated Tables 验证链路。

jOOQ 官方支持从 SQL DDL 文件生成代码，`DDLDatabase` 可以读取一组 SQL script，并支持 ant-style 路径、`sort`、`defaultNameCase` 等配置。([jOOQ][1]) jOOQ 也支持 Maven codegen 插件在 `generate-sources` 阶段执行。([jOOQ][2]) 当前 Phase4.5 已经新增 `aiops-persistence`，但里面还是手写表/字段 helper。

---

# 1. Phase4.6 目标

```txt id="ki0197"
1. 在 aiops-persistence 中增加 jOOQ codegen。
2. 从 apps/aiops-server/src/main/resources/db/migration/*.sql 生成代码。
3. 生成包名：io.aegisops.persistence.jooq
4. 生成目录：target/generated-sources/jooq
5. 保留 AegisJooq / AegisTables，不破坏 Phase4.5。
6. 迁移 JdbcEvidenceRepository 使用 generated Tables。
7. 增加 generated schema smoke test。
8. 增加 Evidence Repository generated SQL 单测。
9. 不改 API，不改 DB migration，不改 Python Agent。
```

---

# 2. 修改文件清单

```txt id="qvw6zf"
pom.xml

modules/aiops-persistence/
  pom.xml
  src/main/resources/jooq-codegen.xml
  src/test/java/io/aegisops/persistence/JooqGeneratedSchemaTest.java
  src/test/java/io/aegisops/persistence/JooqGeneratedDslSmokeTest.java

modules/aiops-evidence/
  pom.xml
  src/main/java/io/aegisops/evidence/JdbcEvidenceRepository.java
  src/test/java/io/aegisops/evidence/JdbcEvidenceRepositoryGeneratedSqlTest.java

docs/mvp/design/phase4.6-jooq-codegen.md
```

---

# 3. 根 `pom.xml`

确认根模块已经有：

```xml id="tr4e4z"
<module>modules/aiops-persistence</module>
```

如果没有，加入：

```xml id="y49wj1"
<modules>
  <module>modules/aiops-common</module>
  <module>modules/aiops-web</module>
  <module>modules/aiops-persistence</module>
  <module>modules/aiops-audit</module>
  <module>modules/aiops-tenant</module>
  <module>modules/aiops-user</module>
  <module>modules/aiops-security</module>
  <module>modules/aiops-zabbix-adapter</module>
  <module>modules/aiops-datasource</module>
  <module>modules/aiops-asset</module>
  <module>modules/aiops-alert</module>
  <module>modules/aiops-incident</module>
  <module>modules/aiops-rca</module>
  <module>modules/aiops-evidence</module>
  <module>modules/aiops-ai-client</module>
  <module>apps/aiops-server</module>
  <module>apps/aiops-worker</module>
  <module>apps/aiops-runner</module>
</modules>
```

---

# 4. 替换 `modules/aiops-persistence/pom.xml`

```xml id="rscctk"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.aegisops</groupId>
    <artifactId>aegisops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>aiops-persistence</artifactId>
  <packaging>jar</packaging>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jooq</artifactId>
    </dependency>

    <!-- Required by org.jooq.meta.extensions.ddl.DDLDatabase. -->
    <dependency>
      <groupId>org.jooq</groupId>
      <artifactId>jooq-meta-extensions</artifactId>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.jooq</groupId>
        <artifactId>jooq-codegen-maven</artifactId>
        <executions>
          <execution>
            <id>generate-aegisops-jooq</id>
            <phase>generate-sources</phase>
            <goals>
              <goal>generate</goal>
            </goals>
            <configuration>
              <configurationFile>${project.basedir}/src/main/resources/jooq-codegen.xml</configurationFile>
            </configuration>
          </execution>
        </executions>
        <dependencies>
          <dependency>
            <groupId>org.jooq</groupId>
            <artifactId>jooq-meta-extensions</artifactId>
            <version>${jooq.version}</version>
          </dependency>
        </dependencies>
      </plugin>

      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>add-jooq-generated-source</id>
            <phase>generate-sources</phase>
            <goals>
              <goal>add-source</goal>
            </goals>
            <configuration>
              <sources>
                <source>${project.build.directory}/generated-sources/jooq</source>
              </sources>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

> 这里使用 `jooq-meta-extensions` 的 `DDLDatabase` 从 Flyway SQL 直接生成 schema。这样不依赖本地 Docker，也不需要 Testcontainers 参与 codegen。jOOQ 文档明确说明 `DDLDatabase` 可用 SQL script 或一组增量文件复现 schema。([jOOQ][1])

---

# 5. 新增 `modules/aiops-persistence/src/main/resources/jooq-codegen.xml`

```xml id="tpv79x"
<?xml version="1.0" encoding="UTF-8"?>
<configuration xmlns="https://www.jooq.org/xsd/jooq-codegen-3.20.0.xsd">
  <logging>WARN</logging>

  <generator>
    <name>org.jooq.codegen.JavaGenerator</name>

    <database>
      <name>org.jooq.meta.extensions.ddl.DDLDatabase</name>

      <properties>
        <!-- Read existing Flyway migrations as the schema source of truth. -->
        <property>
          <key>scripts</key>
          <value>../../apps/aiops-server/src/main/resources/db/migration/*.sql</value>
        </property>

        <!-- Match Flyway migration ordering. -->
        <property>
          <key>sort</key>
          <value>flyway</value>
        </property>

        <!-- PostgreSQL lower-case unquoted identifiers. -->
        <property>
          <key>defaultNameCase</key>
          <value>lower</value>
        </property>

        <property>
          <key>unqualifiedSchema</key>
          <value>public</value>
        </property>

        <!-- Keep parser tolerant of vendor-specific DDL if we later add extensions. -->
        <property>
          <key>parseIgnoreComments</key>
          <value>true</value>
        </property>
      </properties>

      <includes>
        tenant
        |app_user
        |user_role
        |audit_log
        |datasource
        |asset
        |asset_relation
        |alert_event
        |incident
        |incident_event
        |incident_timeline
        |rca_analysis
        |ai_diagnosis
        |agent_run
        |agent_run_step
        |agent_eval_result
        |log_event
        |change_event
      </includes>

      <excludes>
        flyway_schema_history
      </excludes>

      <forcedTypes>
        <forcedType>
          <userType>org.jooq.JSONB</userType>
          <includeTypes>jsonb</includeTypes>
        </forcedType>
      </forcedTypes>
    </database>

    <generate>
      <deprecated>false</deprecated>
      <records>true</records>
      <pojos>false</pojos>
      <daos>false</daos>
      <fluentSetters>true</fluentSetters>
      <javaTimeTypes>true</javaTimeTypes>
    </generate>

    <target>
      <packageName>io.aegisops.persistence.jooq</packageName>
      <directory>target/generated-sources/jooq</directory>
    </target>
  </generator>
</configuration>
```

> 如果后续某个 PostgreSQL 特性 `DDLDatabase` 解析不了，就在 migration 里用 jOOQ 支持的 ignore comments 包起来。jOOQ 文档支持用 `-- [jooq ignore start]` / `-- [jooq ignore stop]` 跳过无法解析的内容。([jOOQ][1])

---

# 6. 保留 `AegisTables`，但标记为过渡层

`modules/aiops-persistence/src/main/java/io/aegisops/persistence/AegisTables.java`

只改类注释即可：

```java id="ayufpm"
package io.aegisops.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * no-codegen jOOQ 表与字段常量。
 *
 * <p>Phase4.5 使用本类作为过渡层。Phase4.6 开始引入 generated Tables，新代码优先使用
 * {@code io.aegisops.persistence.jooq.Tables}。本类保留给尚未迁移的 Repository 使用。
 */
public final class AegisTables {
  private AegisTables() {}

  public static final Table<?> INCIDENT = DSL.table(DSL.name("incident")).as("i");
  public static final Table<?> INCIDENT_EVENT = DSL.table(DSL.name("incident_event")).as("ie");
  public static final Table<?> ALERT_EVENT = DSL.table(DSL.name("alert_event")).as("a");
  public static final Table<?> RCA_ANALYSIS = DSL.table(DSL.name("rca_analysis")).as("r");
  public static final Table<?> ASSET_RELATION = DSL.table(DSL.name("asset_relation")).as("ar");
  public static final Table<?> AI_DIAGNOSIS = DSL.table(DSL.name("ai_diagnosis")).as("ad");
  public static final Table<?> INCIDENT_TIMELINE =
      DSL.table(DSL.name("incident_timeline")).as("it");
  public static final Table<?> AGENT_RUN = DSL.table(DSL.name("agent_run")).as("agr");
  public static final Table<?> AGENT_RUN_STEP = DSL.table(DSL.name("agent_run_step")).as("agrs");
  public static final Table<?> AGENT_EVAL_RESULT =
      DSL.table(DSL.name("agent_eval_result")).as("aer");
  public static final Table<?> LOG_EVENT = DSL.table(DSL.name("log_event")).as("le");
  public static final Table<?> CHANGE_EVENT = DSL.table(DSL.name("change_event")).as("ce");

  public static Field<String> str(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), String.class);
  }

  public static Field<Integer> integer(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Integer.class);
  }

  public static Field<Long> lng(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Long.class);
  }

  public static Field<Boolean> bool(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), Boolean.class);
  }

  public static Field<BigDecimal> decimal(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), BigDecimal.class);
  }

  public static Field<OffsetDateTime> time(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), OffsetDateTime.class);
  }

  public static Field<JSONB> jsonb(Table<?> table, String column) {
    return DSL.field(DSL.name(table.getName(), column), JSONB.class);
  }
}
```

---

# 7. 修改 `modules/aiops-evidence/pom.xml`

确保依赖 `aiops-persistence`，并且不需要直接依赖 `spring-boot-starter-jooq`：

```xml id="img0qu"
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.aegisops</groupId>
    <artifactId>aegisops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>

  <artifactId>aiops-evidence</artifactId>
  <packaging>jar</packaging>

  <dependencies>
    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-common</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-persistence</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>io.aegisops</groupId>
      <artifactId>aiops-web</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
  </dependencies>
</project>
```

---

# 8. 替换 `JdbcEvidenceRepository.java`

路径：

```txt id="xmqc89"
modules/aiops-evidence/src/main/java/io/aegisops/evidence/JdbcEvidenceRepository.java
```

```java id="wd7cky"
package io.aegisops.evidence;

import static io.aegisops.persistence.jooq.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.Tables.LOG_EVENT;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record5;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ generated Tables based evidence repository. */
@Repository
public class JdbcEvidenceRepository implements EvidenceRepository {
  private final DSLContext dsl;

  public JdbcEvidenceRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return LogEvidence.unavailable("Primary asset id and service names are empty.");
    }

    Field<String> severity = LOG_EVENT.SEVERITY;
    Field<String> sample = DSL.min(LOG_EVENT.MESSAGE).as("sample");
    Field<Integer> logCount = DSL.count().as("log_count");
    Field<OffsetDateTime> firstSeenAt = DSL.min(LOG_EVENT.OCCURRED_AT).as("first_seen_at");
    Field<OffsetDateTime> lastSeenAt = DSL.max(LOG_EVENT.OCCURRED_AT).as("last_seen_at");

    List<LogPattern> patterns =
        dsl.select(severity, sample, logCount, firstSeenAt, lastSeenAt)
            .from(LOG_EVENT)
            .where(baseLogCondition(request))
            .groupBy(severity, DSL.field("left({0}, 160)", String.class, LOG_EVENT.MESSAGE))
            .orderBy(logCount.desc(), lastSeenAt.desc())
            .limit(maxPatterns)
            .fetch(this::toLogPattern);

    if (patterns.isEmpty()) {
      return LogEvidence.unavailable("No error log evidence found.");
    }

    return new LogEvidence(true, "", patterns);
  }

  @Override
  public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
    if (isBlank(request.primaryAssetId()) && request.normalizedServiceNames().isEmpty()) {
      return ChangeEvidence.unavailable("Primary asset id and service names are empty.");
    }

    List<ChangeEvidenceEvent> events =
        dsl.select(
                CHANGE_EVENT.ID,
                CHANGE_EVENT.CHANGE_TYPE,
                CHANGE_EVENT.TITLE,
                CHANGE_EVENT.DESCRIPTION,
                CHANGE_EVENT.SOURCE,
                CHANGE_EVENT.OPERATOR,
                CHANGE_EVENT.RISK_LEVEL,
                CHANGE_EVENT.OCCURRED_AT)
            .from(CHANGE_EVENT)
            .where(baseChangeCondition(request))
            .orderBy(CHANGE_EVENT.OCCURRED_AT.desc())
            .limit(maxChanges)
            .fetch(
                record ->
                    new ChangeEvidenceEvent(
                        record.get(CHANGE_EVENT.ID),
                        record.get(CHANGE_EVENT.CHANGE_TYPE),
                        record.get(CHANGE_EVENT.TITLE),
                        record.get(CHANGE_EVENT.DESCRIPTION),
                        record.get(CHANGE_EVENT.SOURCE),
                        record.get(CHANGE_EVENT.OPERATOR),
                        record.get(CHANGE_EVENT.RISK_LEVEL),
                        record.get(CHANGE_EVENT.OCCURRED_AT)));

    if (events.isEmpty()) {
      return ChangeEvidence.unavailable("No change evidence found.");
    }

    return new ChangeEvidence(true, "", events);
  }

  private LogPattern toLogPattern(Record5<String, String, Integer, OffsetDateTime, OffsetDateTime> record) {
    return new LogPattern(
        record.value1(),
        record.value2(),
        numberAsLong(record.value3()),
        record.value4(),
        record.value5());
  }

  private Condition baseLogCondition(EvidenceQueryRequest request) {
    return LOG_EVENT
        .TENANT_ID
        .eq(request.tenantId())
        .and(LOG_EVENT.OCCURRED_AT.ge(request.startedAt()))
        .and(LOG_EVENT.OCCURRED_AT.le(request.lastSeenAt()))
        .and(LOG_EVENT.SEVERITY.in("error", "fatal", "critical", "warn", "warning"))
        .and(logEntityCondition(request));
  }

  private Condition baseChangeCondition(EvidenceQueryRequest request) {
    return CHANGE_EVENT
        .TENANT_ID
        .eq(request.tenantId())
        .and(CHANGE_EVENT.OCCURRED_AT.ge(request.startedAt()))
        .and(CHANGE_EVENT.OCCURRED_AT.le(request.lastSeenAt()))
        .and(changeEntityCondition(request));
  }

  private Condition logEntityCondition(EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();

    if (!isBlank(request.primaryAssetId())) {
      condition = condition.or(LOG_EVENT.ASSET_ID.eq(request.primaryAssetId()));
    }

    if (!request.normalizedServiceNames().isEmpty()) {
      condition = condition.or(LOG_EVENT.SERVICE_NAME.in(request.normalizedServiceNames()));
    }

    return condition;
  }

  private Condition changeEntityCondition(EvidenceQueryRequest request) {
    Condition condition = DSL.falseCondition();

    if (!isBlank(request.primaryAssetId())) {
      condition = condition.or(CHANGE_EVENT.ASSET_ID.eq(request.primaryAssetId()));
    }

    if (!request.normalizedServiceNames().isEmpty()) {
      condition = condition.or(CHANGE_EVENT.SERVICE_NAME.in(request.normalizedServiceNames()));
    }

    return condition;
  }

  private static long numberAsLong(Number value) {
    return value == null ? 0L : value.longValue();
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
```

---

# 9. 新增 generated schema 测试

## 9.1 `JooqGeneratedSchemaTest.java`

路径：

```txt id="ercbb1"
modules/aiops-persistence/src/test/java/io/aegisops/persistence/JooqGeneratedSchemaTest.java
```

```java id="wk8gbr"
package io.aegisops.persistence;

import static io.aegisops.persistence.jooq.Tables.AGENT_RUN;
import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;
import static io.aegisops.persistence.jooq.Tables.CHANGE_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.LOG_EVENT;
import static io.aegisops.persistence.jooq.Tables.RCA_ANALYSIS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class JooqGeneratedSchemaTest {
  @Test
  void generatedCoreTablesExist() {
    assertNotNull(INCIDENT);
    assertNotNull(RCA_ANALYSIS);
    assertNotNull(AI_DIAGNOSIS);
    assertNotNull(AGENT_RUN);
    assertNotNull(LOG_EVENT);
    assertNotNull(CHANGE_EVENT);
  }

  @Test
  void generatedColumnNamesMatchFlywaySchema() {
    assertEquals("tenant_id", INCIDENT.TENANT_ID.getName());
    assertEquals("evidence", RCA_ANALYSIS.EVIDENCE.getName());
    assertEquals("response_raw", AI_DIAGNOSIS.RESPONSE_RAW.getName());
    assertEquals("generation_mode", AGENT_RUN.GENERATION_MODE.getName());
    assertEquals("service_name", LOG_EVENT.SERVICE_NAME.getName());
    assertEquals("change_type", CHANGE_EVENT.CHANGE_TYPE.getName());
  }
}
```

---

## 9.2 `JooqGeneratedDslSmokeTest.java`

路径：

```txt id="y89oxa"
modules/aiops-persistence/src/test/java/io/aegisops/persistence/JooqGeneratedDslSmokeTest.java
```

```java id="qfn3rn"
package io.aegisops.persistence;

import static io.aegisops.persistence.jooq.Tables.ALERT_EVENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT;
import static io.aegisops.persistence.jooq.Tables.INCIDENT_EVENT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class JooqGeneratedDslSmokeTest {
  @Test
  void generatedJoinDslRendersExpectedTables() {
    String sql =
        DSL.using(SQLDialect.POSTGRES)
            .select(ALERT_EVENT.ID, ALERT_EVENT.TITLE)
            .from(INCIDENT_EVENT)
            .join(INCIDENT)
            .on(INCIDENT.ID.eq(INCIDENT_EVENT.INCIDENT_ID))
            .join(ALERT_EVENT)
            .on(ALERT_EVENT.ID.eq(INCIDENT_EVENT.EVENT_ID))
            .where(INCIDENT.TENANT_ID.eq("tenant_1"))
            .and(INCIDENT_EVENT.EVENT_TYPE.eq("alert"))
            .getSQL()
            .toLowerCase();

    assertTrue(sql.contains("incident_event"));
    assertTrue(sql.contains("incident"));
    assertTrue(sql.contains("alert_event"));
    assertTrue(sql.contains("tenant_id"));
  }
}
```

---

# 10. 替换 Evidence Repository 测试

路径：

```txt id="e4kiwv"
modules/aiops-evidence/src/test/java/io/aegisops/evidence/JdbcEvidenceRepositoryGeneratedSqlTest.java
```

```java id="io9g9i"
package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.evidence.dto.EvidenceQueryRequest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

class JdbcEvidenceRepositoryGeneratedSqlTest {
  @Test
  void queryChangesUsesGeneratedTablesAndServiceNameInCondition() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());
          return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        };

    JdbcEvidenceRepository repository =
        new JdbcEvidenceRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    repository.queryChanges(request(), 10);

    String sql = sqlRef.get().toLowerCase();

    assertTrue(sql.contains("change_event"));
    assertTrue(sql.contains("service_name"));
    assertTrue(sql.contains("asset_id"));
    assertTrue(sql.contains("tenant_id"));
  }

  @Test
  void queryLogsMapsGeneratedResult() {
    AtomicReference<String> sqlRef = new AtomicReference<>();

    MockDataProvider provider =
        context -> {
          sqlRef.set(context.sql());

          DSLContext dsl = DSL.using(SQLDialect.POSTGRES);

          var severity = DSL.field("severity", String.class);
          var sample = DSL.field("sample", String.class);
          var logCount = DSL.field("log_count", Integer.class);
          var firstSeenAt = DSL.field("first_seen_at", OffsetDateTime.class);
          var lastSeenAt = DSL.field("last_seen_at", OffsetDateTime.class);

          var result = dsl.newResult(severity, sample, logCount, firstSeenAt, lastSeenAt);

          result.add(
              dsl.newRecord(severity, sample, logCount, firstSeenAt, lastSeenAt)
                  .values(
                      "error",
                      "timeout",
                      3,
                      OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                      OffsetDateTime.parse("2026-06-16T10:00:00+09:00")));

          return new MockResult[] {new MockResult(1, result)};
        };

    JdbcEvidenceRepository repository =
        new JdbcEvidenceRepository(DSL.using(new MockConnection(provider), SQLDialect.POSTGRES));

    var result = repository.queryLogs(request(), 10);

    assertTrue(result.available());
    assertEquals(1, result.patterns().size());
    assertEquals(3L, result.patterns().get(0).count());

    String sql = sqlRef.get().toLowerCase();
    assertTrue(sql.contains("log_event"));
    assertTrue(sql.contains("log_count"));
  }

  @Test
  void queryLogsReturnsUnavailableWhenNoEntityScope() {
    JdbcEvidenceRepository repository = new JdbcEvidenceRepository(DSL.using(SQLDialect.POSTGRES));

    var result =
        repository.queryLogs(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                null,
                OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of(),
                List.of(),
                List.of()),
            10);

    assertTrue(result.reason().contains("Primary asset id and service names are empty"));
  }

  private EvidenceQueryRequest request() {
    return new EvidenceQueryRequest(
        "agent-diagnosis.v1",
        "tenant_1",
        "inc_1",
        "trace_1",
        "asset_1",
        OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
        OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
        List.of("fp_cpu"),
        List.of("CPU high"),
        List.of("checkout-service"));
  }
}
```

> 原来的 `JdbcEvidenceRepositoryJooqSqlTest` 可以删除或改名为这个 generated 版本，避免重复。

---

# 11. 删除旧 Evidence no-codegen 测试

删除：

```txt id="zon792"
modules/aiops-evidence/src/test/java/io/aegisops/evidence/JdbcEvidenceRepositoryJooqSqlTest.java
```

理由：它验证的是 `AegisTables` no-codegen 渲染，Phase4.6 中 Evidence 已迁移到 generated Tables，保留会让测试语义混乱。

---

# 12. 新增设计文档

路径：

```txt id="xd1szc"
docs/mvp/design/phase4.6-jooq-codegen.md
```

```md id="ds9ee3"
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

## 后续

Phase4.7 再迁移 aiops-ai-client 与 aiops-rca 到 generated Tables。
```

---

# 13. 生成代码说明

生成代码不手写提交到正文里，因为它由 Maven 在 `generate-sources` 阶段自动生成，路径是：

```txt id="u3qyt2"
modules/aiops-persistence/target/generated-sources/jooq/io/aegisops/persistence/jooq
```

关键生成类包括：

```txt id="kg3eny"
io.aegisops.persistence.jooq.Tables
io.aegisops.persistence.jooq.tables.Incident
io.aegisops.persistence.jooq.tables.AlertEvent
io.aegisops.persistence.jooq.tables.IncidentEvent
io.aegisops.persistence.jooq.tables.RcaAnalysis
io.aegisops.persistence.jooq.tables.AiDiagnosis
io.aegisops.persistence.jooq.tables.AgentRun
io.aegisops.persistence.jooq.tables.LogEvent
io.aegisops.persistence.jooq.tables.ChangeEvent
```

如果你希望把 generated sources 提交进仓库，也可以把 `<directory>` 改成：

```xml id="dfgpzy"
<directory>src/generated/java</directory>
```

但我不建议现在提交 generated sources。Phase4.6 阶段更适合让 CI 每次生成，避免 schema 改动后忘记同步生成文件。

---

# 14. 验证命令

## 14.1 只验证 codegen

```powershell id="qxjha7"
mvn -pl modules/aiops-persistence -am generate-sources
```

## 14.2 跑 persistence 测试

```powershell id="vf51sz"
mvn -pl modules/aiops-persistence -am test
```

## 14.3 跑 evidence 测试

```powershell id="bmu17n"
mvn -pl modules/aiops-evidence -am test
```

## 14.4 跑 server 聚合测试

```powershell id="em5hlt"
mvn -pl apps/aiops-server -am test
```

## 14.5 全量

```powershell id="aufsti"
mvn test
```

---

# 15. 可能踩坑与处理

## 15.1 DDLDatabase 解析某条 PostgreSQL DDL 失败

处理方式是在 migration 里加 ignore comments：

```sql id="mq2nxf"
-- [jooq ignore start]
create extension if not exists some_extension;
-- [jooq ignore stop]
```

jOOQ 文档支持这个机制。([jOOQ][1])

## 15.2 生成类字段名和预期不同

比如 `source_event_id` 生成字段通常是：

```java id="i0psdn"
SOURCE_EVENT_ID
```

用测试兜住：

```java id="bqew7m"
assertEquals("source_event_id", ALERT_EVENT.SOURCE_EVENT_ID.getName());
```

## 15.3 生成太慢

只在 `aiops-persistence` 执行 codegen，不在每个业务模块重复生成。业务模块只依赖 `aiops-persistence`。

---

# 16. Phase4.6 验收标准

```txt id="gv98r7"
1. mvn -pl modules/aiops-persistence -am generate-sources 成功。
2. target/generated-sources/jooq 生成 Tables。
3. JooqGeneratedSchemaTest 通过。
4. JooqGeneratedDslSmokeTest 通过。
5. JdbcEvidenceRepository 使用 io.aegisops.persistence.jooq.Tables。
6. JdbcEvidenceRepositoryGeneratedSqlTest 通过。
7. aiops-ai-client / aiops-rca 仍能使用 AegisTables 过渡层。
8. apps/aiops-server -am test 通过。
9. 不修改 API contract。
10. 不修改 Python Agent。
```

---

# 17. 后续 Phase4.7

Phase4.6 完成后，Phase4.7 建议做：

```txt id="3hc7yp"
迁移 aiops-ai-client 到 generated Tables
迁移 aiops-rca 到 generated Tables
删除或弱化 AegisTables
增加 generated Table 覆盖测试
```

Phase4.6 的关键价值是先把 **schema → generated Tables** 链路打通。只要这一步稳定，后面 Repository 迁移就是机械替换，不再有架构风险。

[1]: https://www.jooq.org/doc/latest/manual/code-generation/codegen-meta-sources/codegen-ddl/ "DDLDatabase: Code generation from SQL files"
[2]: https://www.jooq.org/doc/latest/manual/code-generation/codegen-maven/ "Running the code generator with Maven"
