# Phase4.5：jOOQ 持久层改造

默认前提：**Phase4 已经完成**，也就是现在已经有：

```txt id="8j4fv2"
apps/aiops-agent                  # Python LangGraph Diagnosis Agent
modules/aiops-ai-client           # Java 调用 Python Agent，并保存 ai_diagnosis
modules/aiops-rca                 # RCA 规则引擎
modules/aiops-incident            # Incident 聚合中心
```

Phase4.5 不做新业务能力，只做 **Persistence Layer Refactor**：把当前复杂的 `JdbcTemplate + SQL text block` 改成 **jOOQ DSL**。

Spring Boot 官方文档也明确写了：jOOQ 会从数据库生成 Java 代码，并通过 fluent API 构建 type-safe SQL；Spring Boot 可以自动配置 `DSLContext` 并连接到应用的 `DataSource`。([Home][1]) jOOQ 官方文档中，`DSLContext` 是持有 jOOQ 配置并用于构造可执行 SQL 的核心入口。([jOOQ][2])

---

# 1. 设计目标

## 1.1 本阶段完成

```txt id="cww7qo"
新增 aiops-persistence 模块
引入 spring-boot-starter-jooq
提供统一 AegisTables 表字段常量
提供 JSONB 工具
提供 Tenant 条件工具
将 Incident / RCA / AI 三块复杂 Repository 改成 jOOQ DSL
保持 Repository interface 不变
保持 Service / Controller 不变
补充完整单元测试
```

## 1.2 本阶段不做

```txt id="3f9xm3"
不引入 JPA
不一次性改所有简单 CRUD
不引入 jOOQ codegen
不改业务表结构
不改 API
不改前端
```

---

# 2. 为什么本阶段先不用 jOOQ Codegen

jOOQ 官方 Maven codegen 插件确实支持在 Maven `generate-sources` 阶段生成代码，官方示例也使用 `jooq-codegen-maven`。([jOOQ][3])

但你这个项目现在还处于 MVP 快速迭代期，如果马上引入 codegen，会多出这些问题：

```txt id="ief90w"
1. 本地必须先有可连接的数据库才能 generate-sources
2. CI 要保证 Flyway migrate 后再 codegen
3. 私有开发环境里 Docker / Windows / WSL 的稳定性还没完全收敛
4. 生成代码体积大，不适合在 Phase4.5 一次性推进
```

所以 Phase4.5 采用：

```txt id="jixbcy"
jOOQ OSS + 手写集中式表字段常量
```

等后面 Phase6 或 Persistence v2 再升级为：

```txt id="xsfrdd"
jOOQ Codegen + Flyway schema source
```

---

# 3. 目标结构

```txt id="xx1rjv"
modules/
  aiops-persistence/
    pom.xml
    src/main/java/io/aegisops/persistence/
      AegisTables.java
      JooqConditions.java
      JooqJson.java
      JooqPersistenceConfiguration.java
    src/test/java/io/aegisops/persistence/
      JooqConditionsTest.java
      JooqJsonTest.java

modules/aiops-incident/
  JdbcIncidentRepository.java    # 内部改成 jOOQ 实现，类名先保持不变

modules/aiops-rca/
  JdbcRcaRepository.java         # 内部改成 jOOQ 实现，类名先保持不变

modules/aiops-ai-client/
  JdbcAiRepository.java          # 内部改成 jOOQ 实现，类名先保持不变
```

保留类名 `JdbcXxxRepository` 是为了减少一次性重命名风险。下一阶段可以再改名为：

```txt id="4nfdmk"
JooqIncidentRepository
JooqRcaRepository
JooqAiRepository
```

---

# 4. Maven 修改

## 4.1 根 `pom.xml`

在 `<modules>` 中新增：

```xml id="pd8j6x"
<module>modules/aiops-persistence</module>
```

建议放在 `aiops-common` 后面：

```xml id="phl2si"
<modules>
    <module>modules/aiops-common</module>
    <module>modules/aiops-persistence</module>
    <module>modules/aiops-web</module>
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
    <module>modules/aiops-ai-client</module>
    <module>apps/aiops-server</module>
    <module>apps/aiops-worker</module>
    <module>apps/aiops-runner</module>
</modules>
```

---

## 4.2 `apps/aiops-server/pom.xml`

新增依赖：

```xml id="p3bxwj"
<dependency>
    <groupId>io.aegisops</groupId>
    <artifactId>aiops-persistence</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 4.3 `modules/aiops-incident/pom.xml`

新增依赖：

```xml id="fofm84"
<dependency>
    <groupId>io.aegisops</groupId>
    <artifactId>aiops-persistence</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 4.4 `modules/aiops-rca/pom.xml`

新增依赖：

```xml id="n6vxc2"
<dependency>
    <groupId>io.aegisops</groupId>
    <artifactId>aiops-persistence</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 4.5 `modules/aiops-ai-client/pom.xml`

新增依赖：

```xml id="4s6msf"
<dependency>
    <groupId>io.aegisops</groupId>
    <artifactId>aiops-persistence</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

# 5. 新增 `aiops-persistence` 模块

## 5.1 `modules/aiops-persistence/pom.xml`

```xml id="pf9nu3"
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
            <groupId>io.aegisops</groupId>
            <artifactId>aiops-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jooq</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

## 5.2 `AegisTables.java`

路径：

```txt id="j9xw2k"
modules/aiops-persistence/src/main/java/io/aegisops/persistence/AegisTables.java
```

```java id="tvff1j"
package io.aegisops.persistence;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

public final class AegisTables {
    private AegisTables() {}

    public static final Table<?> INCIDENT = table(name("incident"));
    public static final Field<String> INCIDENT_ID = field(name("incident", "id"), String.class);
    public static final Field<String> INCIDENT_TENANT_ID = field(name("incident", "tenant_id"), String.class);
    public static final Field<String> INCIDENT_TITLE = field(name("incident", "title"), String.class);
    public static final Field<String> INCIDENT_SUMMARY = field(name("incident", "summary"), String.class);
    public static final Field<String> INCIDENT_SEVERITY = field(name("incident", "severity"), String.class);
    public static final Field<String> INCIDENT_STATUS = field(name("incident", "status"), String.class);
    public static final Field<String> INCIDENT_SOURCE = field(name("incident", "source"), String.class);
    public static final Field<String> INCIDENT_PRIMARY_ASSET_ID = field(name("incident", "primary_asset_id"), String.class);
    public static final Field<String> INCIDENT_AGGREGATION_KEY = field(name("incident", "aggregation_key"), String.class);
    public static final Field<Integer> INCIDENT_ALERT_COUNT = field(name("incident", "alert_count"), Integer.class);
    public static final Field<BigDecimal> INCIDENT_IMPACT_SCORE = field(name("incident", "impact_score"), BigDecimal.class);
    public static final Field<String> INCIDENT_SUSPECTED_ROOT_CAUSE = field(name("incident", "suspected_root_cause"), String.class);
    public static final Field<BigDecimal> INCIDENT_CONFIDENCE = field(name("incident", "confidence"), BigDecimal.class);
    public static final Field<OffsetDateTime> INCIDENT_STARTED_AT = field(name("incident", "started_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> INCIDENT_DETECTED_AT = field(name("incident", "detected_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> INCIDENT_LAST_SEEN_AT = field(name("incident", "last_seen_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> INCIDENT_RESOLVED_AT = field(name("incident", "resolved_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> INCIDENT_CREATED_AT = field(name("incident", "created_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> INCIDENT_UPDATED_AT = field(name("incident", "updated_at"), OffsetDateTime.class);

    public static final Table<?> INCIDENT_EVENT = table(name("incident_event"));
    public static final Field<String> INCIDENT_EVENT_ID = field(name("incident_event", "id"), String.class);
    public static final Field<String> INCIDENT_EVENT_INCIDENT_ID = field(name("incident_event", "incident_id"), String.class);
    public static final Field<String> INCIDENT_EVENT_EVENT_TYPE = field(name("incident_event", "event_type"), String.class);
    public static final Field<String> INCIDENT_EVENT_EVENT_ID = field(name("incident_event", "event_id"), String.class);
    public static final Field<String> INCIDENT_EVENT_RELATION_TYPE = field(name("incident_event", "relation_type"), String.class);
    public static final Field<OffsetDateTime> INCIDENT_EVENT_OCCURRED_AT = field(name("incident_event", "occurred_at"), OffsetDateTime.class);

    public static final Table<?> INCIDENT_TIMELINE = table(name("incident_timeline"));
    public static final Field<String> INCIDENT_TIMELINE_ID = field(name("incident_timeline", "id"), String.class);
    public static final Field<String> INCIDENT_TIMELINE_INCIDENT_ID = field(name("incident_timeline", "incident_id"), String.class);
    public static final Field<OffsetDateTime> INCIDENT_TIMELINE_EVENT_TIME = field(name("incident_timeline", "event_time"), OffsetDateTime.class);
    public static final Field<String> INCIDENT_TIMELINE_EVENT_TYPE = field(name("incident_timeline", "event_type"), String.class);
    public static final Field<String> INCIDENT_TIMELINE_TITLE = field(name("incident_timeline", "title"), String.class);
    public static final Field<String> INCIDENT_TIMELINE_DESCRIPTION = field(name("incident_timeline", "description"), String.class);
    public static final Field<String> INCIDENT_TIMELINE_SOURCE = field(name("incident_timeline", "source"), String.class);
    public static final Field<JSONB> INCIDENT_TIMELINE_PAYLOAD = field(name("incident_timeline", "payload"), JSONB.class);

    public static final Table<?> ALERT_EVENT = table(name("alert_event"));
    public static final Field<String> ALERT_EVENT_ID = field(name("alert_event", "id"), String.class);
    public static final Field<String> ALERT_EVENT_TENANT_ID = field(name("alert_event", "tenant_id"), String.class);
    public static final Field<String> ALERT_EVENT_SOURCE = field(name("alert_event", "source"), String.class);
    public static final Field<String> ALERT_EVENT_SOURCE_EVENT_ID = field(name("alert_event", "source_event_id"), String.class);
    public static final Field<String> ALERT_EVENT_SEVERITY = field(name("alert_event", "severity"), String.class);
    public static final Field<String> ALERT_EVENT_TITLE = field(name("alert_event", "title"), String.class);
    public static final Field<String> ALERT_EVENT_DESCRIPTION = field(name("alert_event", "description"), String.class);
    public static final Field<String> ALERT_EVENT_STATUS = field(name("alert_event", "status"), String.class);
    public static final Field<String> ALERT_EVENT_ASSET_ID = field(name("alert_event", "asset_id"), String.class);
    public static final Field<String> ALERT_EVENT_ENTITY_TYPE = field(name("alert_event", "entity_type"), String.class);
    public static final Field<String> ALERT_EVENT_ENTITY_NAME = field(name("alert_event", "entity_name"), String.class);
    public static final Field<String> ALERT_EVENT_FINGERPRINT = field(name("alert_event", "fingerprint"), String.class);
    public static final Field<JSONB> ALERT_EVENT_LABELS = field(name("alert_event", "labels"), JSONB.class);
    public static final Field<OffsetDateTime> ALERT_EVENT_STARTS_AT = field(name("alert_event", "starts_at"), OffsetDateTime.class);
    public static final Field<OffsetDateTime> ALERT_EVENT_CREATED_AT = field(name("alert_event", "created_at"), OffsetDateTime.class);

    public static final Table<?> RCA_ANALYSIS = table(name("rca_analysis"));
    public static final Field<String> RCA_ANALYSIS_ID = field(name("rca_analysis", "id"), String.class);
    public static final Field<String> RCA_ANALYSIS_TENANT_ID = field(name("rca_analysis", "tenant_id"), String.class);
    public static final Field<String> RCA_ANALYSIS_INCIDENT_ID = field(name("rca_analysis", "incident_id"), String.class);
    public static final Field<String> RCA_ANALYSIS_STATUS = field(name("rca_analysis", "status"), String.class);
    public static final Field<String> RCA_ANALYSIS_SUSPECTED_ROOT_CAUSE = field(name("rca_analysis", "suspected_root_cause"), String.class);
    public static final Field<BigDecimal> RCA_ANALYSIS_CONFIDENCE = field(name("rca_analysis", "confidence"), BigDecimal.class);
    public static final Field<String> RCA_ANALYSIS_SUMMARY = field(name("rca_analysis", "summary"), String.class);
    public static final Field<JSONB> RCA_ANALYSIS_EVIDENCE = field(name("rca_analysis", "evidence"), JSONB.class);
    public static final Field<String> RCA_ANALYSIS_MODEL_VERSION = field(name("rca_analysis", "model_version"), String.class);
    public static final Field<OffsetDateTime> RCA_ANALYSIS_CREATED_AT = field(name("rca_analysis", "created_at"), OffsetDateTime.class);

    public static final Table<?> ASSET_RELATION = table(name("asset_relation"));
    public static final Field<String> ASSET_RELATION_ID = field(name("asset_relation", "id"), String.class);
    public static final Field<String> ASSET_RELATION_TENANT_ID = field(name("asset_relation", "tenant_id"), String.class);
    public static final Field<String> ASSET_RELATION_FROM_ASSET_ID = field(name("asset_relation", "from_asset_id"), String.class);
    public static final Field<String> ASSET_RELATION_TO_ASSET_ID = field(name("asset_relation", "to_asset_id"), String.class);
    public static final Field<String> ASSET_RELATION_RELATION_TYPE = field(name("asset_relation", "relation_type"), String.class);
    public static final Field<BigDecimal> ASSET_RELATION_CONFIDENCE = field(name("asset_relation", "confidence"), BigDecimal.class);
    public static final Field<String> ASSET_RELATION_SOURCE = field(name("asset_relation", "source"), String.class);

    public static final Table<?> AI_DIAGNOSIS = table(name("ai_diagnosis"));
    public static final Field<String> AI_DIAGNOSIS_ID = field(name("ai_diagnosis", "id"), String.class);
    public static final Field<String> AI_DIAGNOSIS_TENANT_ID = field(name("ai_diagnosis", "tenant_id"), String.class);
    public static final Field<String> AI_DIAGNOSIS_INCIDENT_ID = field(name("ai_diagnosis", "incident_id"), String.class);
    public static final Field<String> AI_DIAGNOSIS_STATUS = field(name("ai_diagnosis", "status"), String.class);
    public static final Field<String> AI_DIAGNOSIS_PROVIDER = field(name("ai_diagnosis", "provider"), String.class);
    public static final Field<String> AI_DIAGNOSIS_MODEL = field(name("ai_diagnosis", "model"), String.class);
    public static final Field<String> AI_DIAGNOSIS_AGENT_NAME = field(name("ai_diagnosis", "agent_name"), String.class);
    public static final Field<JSONB> AI_DIAGNOSIS_REQUEST_PAYLOAD = field(name("ai_diagnosis", "request_payload"), JSONB.class);
    public static final Field<JSONB> AI_DIAGNOSIS_RESPONSE_RAW = field(name("ai_diagnosis", "response_raw"), JSONB.class);
    public static final Field<String> AI_DIAGNOSIS_SUMMARY = field(name("ai_diagnosis", "summary"), String.class);
    public static final Field<String> AI_DIAGNOSIS_ROOT_CAUSE = field(name("ai_diagnosis", "root_cause"), String.class);
    public static final Field<String> AI_DIAGNOSIS_IMPACT = field(name("ai_diagnosis", "impact"), String.class);
    public static final Field<JSONB> AI_DIAGNOSIS_NEXT_STEPS = field(name("ai_diagnosis", "next_steps"), JSONB.class);
    public static final Field<JSONB> AI_DIAGNOSIS_RUNBOOK_SUGGESTIONS = field(name("ai_diagnosis", "runbook_suggestions"), JSONB.class);
    public static final Field<JSONB> AI_DIAGNOSIS_RISKS = field(name("ai_diagnosis", "risks"), JSONB.class);
    public static final Field<OffsetDateTime> AI_DIAGNOSIS_CREATED_AT = field(name("ai_diagnosis", "created_at"), OffsetDateTime.class);
}
```

---

## 5.3 `JooqJson.java`

路径：

```txt id="esgd7w"
modules/aiops-persistence/src/main/java/io/aegisops/persistence/JooqJson.java
```

```java id="bmn58g"
package io.aegisops.persistence;

import org.jooq.Field;
import org.jooq.JSONB;

import static org.jooq.impl.DSL.field;

public final class JooqJson {
    private JooqJson() {}

    public static JSONB jsonb(String json) {
        if (json == null || json.isBlank()) {
            return JSONB.valueOf("{}");
        }
        return JSONB.valueOf(json);
    }

    public static JSONB jsonbArray(String json) {
        if (json == null || json.isBlank()) {
            return JSONB.valueOf("[]");
        }
        return JSONB.valueOf(json);
    }

    public static Field<String> jsonbText(Field<JSONB> field, String alias) {
        return field("{0}::text", String.class, field).as(alias);
    }
}
```

---

## 5.4 `JooqConditions.java`

路径：

```txt id="xjqxku"
modules/aiops-persistence/src/main/java/io/aegisops/persistence/JooqConditions.java
```

```java id="zxg3da"
package io.aegisops.persistence;

import org.jooq.Condition;
import org.jooq.Field;

import java.util.Collection;
import java.util.List;

import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.trueCondition;

public final class JooqConditions {
    private JooqConditions() {}

    public static Condition tenant(Field<String> tenantField, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return falseCondition();
        }
        return tenantField.eq(tenantId);
    }

    public static Condition optionalEq(Field<String> field, String value) {
        if (value == null || value.isBlank()) {
            return trueCondition();
        }
        return field.eq(value);
    }

    public static Condition inOrFalse(Field<String> field, Collection<String> values) {
        List<String> normalized = values == null
                ? List.of()
                : values.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList();

        if (normalized.isEmpty()) {
            return falseCondition();
        }

        return field.in(normalized);
    }
}
```

---

## 5.5 `JooqPersistenceConfiguration.java`

路径：

```txt id="pfolzc"
modules/aiops-persistence/src/main/java/io/aegisops/persistence/JooqPersistenceConfiguration.java
```

```java id="gi5h97"
package io.aegisops.persistence;

import org.jooq.conf.RenderQuotedNames;
import org.jooq.conf.Settings;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqPersistenceConfiguration {
    @Bean
    public DefaultConfigurationCustomizer aegisJooqCustomizer() {
        return configuration -> configuration.set(new Settings()
                .withRenderQuotedNames(RenderQuotedNames.NEVER)
                .withRenderSchema(false)
        );
    }
}
```

---

# 6. 替换 Incident Repository 实现

## `modules/aiops-incident/src/main/java/io/aegisops/incident/JdbcIncidentRepository.java`

```java id="m1zx1b"
package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import io.aegisops.persistence.JooqJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static io.aegisops.persistence.AegisTables.*;
import static io.aegisops.persistence.JooqConditions.tenant;
import static org.jooq.impl.DSL.*;

@Repository
public class JdbcIncidentRepository implements IncidentRepository {
    private final DSLContext dsl;

    public JdbcIncidentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void acquireTenantAggregationLock(String tenantId) {
        dsl.select(field("pg_advisory_xact_lock(hashtext('incident_aggregate'), hashtext({0}))", Object.class, val(tenantId)))
                .fetch();
    }

    @Override
    public List<AlertCandidate> findOpenAlertCandidates(String tenantId, OffsetDateTime since, int limit) {
        return dsl.select(
                        ALERT_EVENT_ID,
                        ALERT_EVENT_TENANT_ID,
                        ALERT_EVENT_SOURCE,
                        ALERT_EVENT_SOURCE_EVENT_ID,
                        ALERT_EVENT_SEVERITY,
                        ALERT_EVENT_TITLE,
                        ALERT_EVENT_DESCRIPTION,
                        ALERT_EVENT_ASSET_ID,
                        ALERT_EVENT_ENTITY_TYPE,
                        ALERT_EVENT_ENTITY_NAME,
                        ALERT_EVENT_FINGERPRINT,
                        ALERT_EVENT_STARTS_AT,
                        ALERT_EVENT_CREATED_AT
                )
                .from(ALERT_EVENT)
                .where(tenant(ALERT_EVENT_TENANT_ID, tenantId))
                .and(ALERT_EVENT_STATUS.eq("open"))
                .and(ALERT_EVENT_STARTS_AT.ge(since))
                .andNotExists(
                        selectOne()
                                .from(INCIDENT_EVENT)
                                .where(INCIDENT_EVENT_EVENT_TYPE.eq("alert"))
                                .and(INCIDENT_EVENT_EVENT_ID.eq(ALERT_EVENT_ID))
                )
                .orderBy(ALERT_EVENT_STARTS_AT.asc())
                .limit(limit)
                .fetch(record -> new AlertCandidate(
                        record.get(ALERT_EVENT_ID),
                        record.get(ALERT_EVENT_TENANT_ID),
                        record.get(ALERT_EVENT_SOURCE),
                        record.get(ALERT_EVENT_SOURCE_EVENT_ID),
                        record.get(ALERT_EVENT_SEVERITY),
                        record.get(ALERT_EVENT_TITLE),
                        record.get(ALERT_EVENT_DESCRIPTION),
                        record.get(ALERT_EVENT_ASSET_ID),
                        record.get(ALERT_EVENT_ENTITY_TYPE),
                        record.get(ALERT_EVENT_ENTITY_NAME),
                        record.get(ALERT_EVENT_FINGERPRINT),
                        record.get(ALERT_EVENT_STARTS_AT),
                        record.get(ALERT_EVENT_CREATED_AT)
                ));
    }

    @Override
    public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
        return baseIncidentSelect()
                .from(INCIDENT)
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .orderBy(INCIDENT_STARTED_AT.desc())
                .limit(limit)
                .fetch(this::toSummary);
    }

    @Override
    public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
        return baseIncidentSelect()
                .from(INCIDENT)
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .fetchOptional(this::toSummary);
    }

    @Override
    public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(String tenantId, String aggregationKey) {
        return baseIncidentSelect()
                .from(INCIDENT)
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_AGGREGATION_KEY.eq(aggregationKey))
                .and(INCIDENT_STATUS.in("open", "investigating", "mitigating"))
                .orderBy(INCIDENT_STARTED_AT.desc())
                .limit(1)
                .fetchOptional(this::toSummary);
    }

    @Override
    public void insertIncident(IncidentCreateCommand command) {
        dsl.insertInto(INCIDENT)
                .set(INCIDENT_ID, command.id())
                .set(INCIDENT_TENANT_ID, command.tenantId())
                .set(INCIDENT_TITLE, command.title())
                .set(INCIDENT_SUMMARY, command.summary())
                .set(INCIDENT_SEVERITY, command.severity())
                .set(INCIDENT_STATUS, "open")
                .set(INCIDENT_SOURCE, command.source())
                .set(INCIDENT_PRIMARY_ASSET_ID, command.primaryAssetId())
                .set(INCIDENT_AGGREGATION_KEY, command.aggregationKey())
                .set(INCIDENT_ALERT_COUNT, command.alertCount())
                .set(INCIDENT_IMPACT_SCORE, BigDecimal.ZERO)
                .set(INCIDENT_STARTED_AT, command.startedAt())
                .set(INCIDENT_DETECTED_AT, command.detectedAt())
                .set(INCIDENT_LAST_SEEN_AT, command.lastSeenAt())
                .set(INCIDENT_CREATED_AT, OffsetDateTime.now())
                .set(INCIDENT_UPDATED_AT, OffsetDateTime.now())
                .execute();
    }

    @Override
    public void updateIncidentAggregation(
            String tenantId,
            String incidentId,
            String title,
            String summary,
            String severity,
            int alertCount,
            OffsetDateTime lastSeenAt
    ) {
        dsl.update(INCIDENT)
                .set(INCIDENT_TITLE, title)
                .set(INCIDENT_SUMMARY, summary)
                .set(INCIDENT_SEVERITY, severity)
                .set(INCIDENT_ALERT_COUNT, alertCount)
                .set(INCIDENT_LAST_SEEN_AT, lastSeenAt)
                .set(INCIDENT_UPDATED_AT, OffsetDateTime.now())
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .execute();
    }

    @Override
    public boolean linkAlert(String id, String incidentId, String alertId, String relationType, OffsetDateTime occurredAt) {
        int updated = dsl.insertInto(INCIDENT_EVENT)
                .set(INCIDENT_EVENT_ID, id)
                .set(INCIDENT_EVENT_INCIDENT_ID, incidentId)
                .set(INCIDENT_EVENT_EVENT_TYPE, "alert")
                .set(INCIDENT_EVENT_EVENT_ID, alertId)
                .set(INCIDENT_EVENT_RELATION_TYPE, relationType)
                .set(INCIDENT_EVENT_OCCURRED_AT, occurredAt)
                .onConflictDoNothing()
                .execute();

        return updated > 0;
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
        dsl.insertInto(INCIDENT_TIMELINE)
                .set(INCIDENT_TIMELINE_ID, command.id())
                .set(INCIDENT_TIMELINE_INCIDENT_ID, command.incidentId())
                .set(INCIDENT_TIMELINE_EVENT_TIME, command.eventTime())
                .set(INCIDENT_TIMELINE_EVENT_TYPE, command.eventType())
                .set(INCIDENT_TIMELINE_TITLE, command.title())
                .set(INCIDENT_TIMELINE_DESCRIPTION, command.description())
                .set(INCIDENT_TIMELINE_SOURCE, command.source())
                .set(INCIDENT_TIMELINE_PAYLOAD, JooqJson.jsonb(command.payloadJson()))
                .execute();
    }

    @Override
    public List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        ensureIncidentBelongsToTenant(tenantId, incidentId);

        return dsl.select(
                        ALERT_EVENT_ID,
                        ALERT_EVENT_SOURCE,
                        ALERT_EVENT_SOURCE_EVENT_ID,
                        ALERT_EVENT_SEVERITY,
                        ALERT_EVENT_TITLE,
                        ALERT_EVENT_STATUS,
                        ALERT_EVENT_ASSET_ID,
                        ALERT_EVENT_ENTITY_NAME,
                        ALERT_EVENT_FINGERPRINT,
                        ALERT_EVENT_STARTS_AT,
                        INCIDENT_EVENT_RELATION_TYPE
                )
                .from(INCIDENT_EVENT)
                .join(ALERT_EVENT).on(ALERT_EVENT_ID.eq(INCIDENT_EVENT_EVENT_ID))
                .where(INCIDENT_EVENT_INCIDENT_ID.eq(incidentId))
                .and(INCIDENT_EVENT_EVENT_TYPE.eq("alert"))
                .and(tenant(ALERT_EVENT_TENANT_ID, tenantId))
                .orderBy(ALERT_EVENT_STARTS_AT.asc())
                .fetch(record -> new IncidentAlertRecord(
                        record.get(ALERT_EVENT_ID),
                        record.get(ALERT_EVENT_SOURCE),
                        record.get(ALERT_EVENT_SOURCE_EVENT_ID),
                        record.get(ALERT_EVENT_SEVERITY),
                        record.get(ALERT_EVENT_TITLE),
                        record.get(ALERT_EVENT_STATUS),
                        record.get(ALERT_EVENT_ASSET_ID),
                        record.get(ALERT_EVENT_ENTITY_NAME),
                        record.get(ALERT_EVENT_FINGERPRINT),
                        record.get(ALERT_EVENT_STARTS_AT),
                        record.get(INCIDENT_EVENT_RELATION_TYPE)
                ));
    }

    @Override
    public List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId) {
        ensureIncidentBelongsToTenant(tenantId, incidentId);

        Field<String> payloadText = JooqJson.jsonbText(INCIDENT_TIMELINE_PAYLOAD, "payload_json");

        return dsl.select(
                        INCIDENT_TIMELINE_ID,
                        INCIDENT_TIMELINE_EVENT_TIME,
                        INCIDENT_TIMELINE_EVENT_TYPE,
                        INCIDENT_TIMELINE_TITLE,
                        INCIDENT_TIMELINE_DESCRIPTION,
                        INCIDENT_TIMELINE_SOURCE,
                        payloadText
                )
                .from(INCIDENT_TIMELINE)
                .where(INCIDENT_TIMELINE_INCIDENT_ID.eq(incidentId))
                .orderBy(INCIDENT_TIMELINE_EVENT_TIME.asc())
                .fetch(record -> new IncidentTimelineRecord(
                        record.get(INCIDENT_TIMELINE_ID),
                        record.get(INCIDENT_TIMELINE_EVENT_TIME),
                        record.get(INCIDENT_TIMELINE_EVENT_TYPE),
                        record.get(INCIDENT_TIMELINE_TITLE),
                        record.get(INCIDENT_TIMELINE_DESCRIPTION),
                        record.get(INCIDENT_TIMELINE_SOURCE),
                        record.get(payloadText)
                ));
    }

    @Override
    public int countLinkedAlerts(String incidentId) {
        Integer count = dsl.selectCount()
                .from(INCIDENT_EVENT)
                .where(INCIDENT_EVENT_INCIDENT_ID.eq(incidentId))
                .and(INCIDENT_EVENT_EVENT_TYPE.eq("alert"))
                .fetchOne(0, Integer.class);

        return count == null ? 0 : count;
    }

    @Override
    public void updateStatus(String tenantId, String incidentId, String status, boolean terminal) {
        int updated = dsl.update(INCIDENT)
                .set(INCIDENT_STATUS, status)
                .set(INCIDENT_RESOLVED_AT, terminal ? OffsetDateTime.now() : null)
                .set(INCIDENT_UPDATED_AT, OffsetDateTime.now())
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .execute();

        if (updated == 0) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private void ensureIncidentBelongsToTenant(String tenantId, String incidentId) {
        boolean exists = dsl.fetchExists(
                selectOne()
                        .from(INCIDENT)
                        .where(tenant(INCIDENT_TENANT_ID, tenantId))
                        .and(INCIDENT_ID.eq(incidentId))
        );

        if (!exists) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private org.jooq.SelectSelectStep<?> baseIncidentSelect() {
        return dsl.select(
                INCIDENT_ID,
                INCIDENT_TENANT_ID,
                INCIDENT_TITLE,
                INCIDENT_SUMMARY,
                INCIDENT_SEVERITY,
                INCIDENT_STATUS,
                INCIDENT_SOURCE,
                INCIDENT_PRIMARY_ASSET_ID,
                INCIDENT_AGGREGATION_KEY,
                INCIDENT_ALERT_COUNT,
                INCIDENT_IMPACT_SCORE,
                INCIDENT_STARTED_AT,
                INCIDENT_DETECTED_AT,
                INCIDENT_LAST_SEEN_AT,
                INCIDENT_RESOLVED_AT,
                INCIDENT_CREATED_AT,
                INCIDENT_UPDATED_AT
        );
    }

    private IncidentSummaryRecord toSummary(org.jooq.Record record) {
        return new IncidentSummaryRecord(
                record.get(INCIDENT_ID),
                record.get(INCIDENT_TENANT_ID),
                record.get(INCIDENT_TITLE),
                record.get(INCIDENT_SUMMARY),
                record.get(INCIDENT_SEVERITY),
                record.get(INCIDENT_STATUS),
                record.get(INCIDENT_SOURCE),
                record.get(INCIDENT_PRIMARY_ASSET_ID),
                record.get(INCIDENT_AGGREGATION_KEY),
                record.get(INCIDENT_ALERT_COUNT) == null ? 0 : record.get(INCIDENT_ALERT_COUNT),
                record.get(INCIDENT_IMPACT_SCORE),
                record.get(INCIDENT_STARTED_AT),
                record.get(INCIDENT_DETECTED_AT),
                record.get(INCIDENT_LAST_SEEN_AT),
                record.get(INCIDENT_RESOLVED_AT),
                record.get(INCIDENT_CREATED_AT),
                record.get(INCIDENT_UPDATED_AT)
        );
    }
}
```

---

# 7. 替换 RCA Repository 实现

## `modules/aiops-rca/src/main/java/io/aegisops/rca/JdbcRcaRepository.java`

```java id="4nkm11"
package io.aegisops.rca;

import io.aegisops.persistence.JooqJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static io.aegisops.persistence.AegisTables.*;
import static io.aegisops.persistence.JooqConditions.inOrFalse;
import static io.aegisops.persistence.JooqConditions.tenant;

@Repository
public class JdbcRcaRepository implements RcaRepository {
    private final DSLContext dsl;

    public JdbcRcaRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
        return dsl.select(
                        INCIDENT_ID,
                        INCIDENT_TENANT_ID,
                        INCIDENT_TITLE,
                        INCIDENT_SUMMARY,
                        INCIDENT_SEVERITY,
                        INCIDENT_STATUS,
                        INCIDENT_SOURCE,
                        INCIDENT_PRIMARY_ASSET_ID,
                        INCIDENT_AGGREGATION_KEY,
                        INCIDENT_ALERT_COUNT,
                        INCIDENT_SUSPECTED_ROOT_CAUSE,
                        INCIDENT_CONFIDENCE,
                        INCIDENT_STARTED_AT,
                        INCIDENT_DETECTED_AT,
                        INCIDENT_LAST_SEEN_AT,
                        INCIDENT_RESOLVED_AT,
                        INCIDENT_CREATED_AT,
                        INCIDENT_UPDATED_AT
                )
                .from(INCIDENT)
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .fetchOptional(record -> new RcaIncidentRecord(
                        record.get(INCIDENT_ID),
                        record.get(INCIDENT_TENANT_ID),
                        record.get(INCIDENT_TITLE),
                        record.get(INCIDENT_SUMMARY),
                        record.get(INCIDENT_SEVERITY),
                        record.get(INCIDENT_STATUS),
                        record.get(INCIDENT_SOURCE),
                        record.get(INCIDENT_PRIMARY_ASSET_ID),
                        record.get(INCIDENT_AGGREGATION_KEY),
                        record.get(INCIDENT_ALERT_COUNT) == null ? 0 : record.get(INCIDENT_ALERT_COUNT),
                        record.get(INCIDENT_SUSPECTED_ROOT_CAUSE),
                        record.get(INCIDENT_CONFIDENCE),
                        record.get(INCIDENT_STARTED_AT),
                        record.get(INCIDENT_DETECTED_AT),
                        record.get(INCIDENT_LAST_SEEN_AT),
                        record.get(INCIDENT_RESOLVED_AT),
                        record.get(INCIDENT_CREATED_AT),
                        record.get(INCIDENT_UPDATED_AT)
                ));
    }

    @Override
    public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        Field<String> labelsText = JooqJson.jsonbText(ALERT_EVENT_LABELS, "labels_json");

        return dsl.select(
                        ALERT_EVENT_ID,
                        ALERT_EVENT_SOURCE,
                        ALERT_EVENT_SOURCE_EVENT_ID,
                        ALERT_EVENT_SEVERITY,
                        ALERT_EVENT_TITLE,
                        ALERT_EVENT_DESCRIPTION,
                        ALERT_EVENT_ASSET_ID,
                        ALERT_EVENT_ENTITY_TYPE,
                        ALERT_EVENT_ENTITY_NAME,
                        ALERT_EVENT_FINGERPRINT,
                        labelsText,
                        ALERT_EVENT_STARTS_AT,
                        ALERT_EVENT_CREATED_AT
                )
                .from(INCIDENT_EVENT)
                .join(INCIDENT).on(INCIDENT_ID.eq(INCIDENT_EVENT_INCIDENT_ID))
                .join(ALERT_EVENT).on(ALERT_EVENT_ID.eq(INCIDENT_EVENT_EVENT_ID))
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .and(INCIDENT_EVENT_EVENT_TYPE.eq("alert"))
                .and(tenant(ALERT_EVENT_TENANT_ID, tenantId))
                .orderBy(ALERT_EVENT_STARTS_AT.asc())
                .fetch(record -> new RcaAlertRecord(
                        record.get(ALERT_EVENT_ID),
                        record.get(ALERT_EVENT_SOURCE),
                        record.get(ALERT_EVENT_SOURCE_EVENT_ID),
                        record.get(ALERT_EVENT_SEVERITY),
                        record.get(ALERT_EVENT_TITLE),
                        record.get(ALERT_EVENT_DESCRIPTION),
                        record.get(ALERT_EVENT_ASSET_ID),
                        record.get(ALERT_EVENT_ENTITY_TYPE),
                        record.get(ALERT_EVENT_ENTITY_NAME),
                        record.get(ALERT_EVENT_FINGERPRINT),
                        record.get(labelsText),
                        record.get(ALERT_EVENT_STARTS_AT),
                        record.get(ALERT_EVENT_CREATED_AT)
                ));
    }

    @Override
    public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
        return dsl.select(
                        ASSET_RELATION_ID,
                        ASSET_RELATION_FROM_ASSET_ID,
                        ASSET_RELATION_TO_ASSET_ID,
                        ASSET_RELATION_RELATION_TYPE,
                        ASSET_RELATION_CONFIDENCE,
                        ASSET_RELATION_SOURCE
                )
                .from(ASSET_RELATION)
                .where(tenant(ASSET_RELATION_TENANT_ID, tenantId))
                .and(
                        inOrFalse(ASSET_RELATION_FROM_ASSET_ID, assetIds)
                                .or(inOrFalse(ASSET_RELATION_TO_ASSET_ID, assetIds))
                )
                .orderBy(ASSET_RELATION_CONFIDENCE.desc())
                .fetch(record -> new RcaAssetRelationRecord(
                        record.get(ASSET_RELATION_ID),
                        record.get(ASSET_RELATION_FROM_ASSET_ID),
                        record.get(ASSET_RELATION_TO_ASSET_ID),
                        record.get(ASSET_RELATION_RELATION_TYPE),
                        record.get(ASSET_RELATION_CONFIDENCE),
                        record.get(ASSET_RELATION_SOURCE)
                ));
    }

    @Override
    public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
        return findAnalysisSelect()
                .where(tenant(RCA_ANALYSIS_TENANT_ID, tenantId))
                .and(RCA_ANALYSIS_INCIDENT_ID.eq(incidentId))
                .orderBy(RCA_ANALYSIS_CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::toAnalysis);
    }

    @Override
    public void saveAnalysis(
            String id,
            String tenantId,
            String incidentId,
            String suspectedRootCause,
            BigDecimal confidence,
            String summary,
            String evidenceJson,
            String modelVersion
    ) {
        dsl.insertInto(RCA_ANALYSIS)
                .set(RCA_ANALYSIS_ID, id)
                .set(RCA_ANALYSIS_TENANT_ID, tenantId)
                .set(RCA_ANALYSIS_INCIDENT_ID, incidentId)
                .set(RCA_ANALYSIS_STATUS, "completed")
                .set(RCA_ANALYSIS_SUSPECTED_ROOT_CAUSE, suspectedRootCause)
                .set(RCA_ANALYSIS_CONFIDENCE, confidence)
                .set(RCA_ANALYSIS_SUMMARY, summary)
                .set(RCA_ANALYSIS_EVIDENCE, JooqJson.jsonbArray(evidenceJson))
                .set(RCA_ANALYSIS_MODEL_VERSION, modelVersion)
                .set(RCA_ANALYSIS_CREATED_AT, OffsetDateTime.now())
                .execute();
    }

    @Override
    public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
        return findAnalysisSelect()
                .where(tenant(RCA_ANALYSIS_TENANT_ID, tenantId))
                .and(RCA_ANALYSIS_ID.eq(id))
                .fetchOptional(this::toAnalysis);
    }

    @Override
    public void updateIncidentRca(String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
        dsl.update(INCIDENT)
                .set(INCIDENT_SUSPECTED_ROOT_CAUSE, suspectedRootCause)
                .set(INCIDENT_CONFIDENCE, confidence)
                .set(INCIDENT_UPDATED_AT, OffsetDateTime.now())
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .execute();
    }

    @Override
    public void addIncidentTimeline(
            String id,
            String incidentId,
            OffsetDateTime eventTime,
            String title,
            String description,
            String payloadJson
    ) {
        dsl.insertInto(INCIDENT_TIMELINE)
                .set(INCIDENT_TIMELINE_ID, id)
                .set(INCIDENT_TIMELINE_INCIDENT_ID, incidentId)
                .set(INCIDENT_TIMELINE_EVENT_TIME, eventTime)
                .set(INCIDENT_TIMELINE_EVENT_TYPE, "rca_analyzed")
                .set(INCIDENT_TIMELINE_TITLE, title)
                .set(INCIDENT_TIMELINE_DESCRIPTION, description)
                .set(INCIDENT_TIMELINE_SOURCE, "system")
                .set(INCIDENT_TIMELINE_PAYLOAD, JooqJson.jsonb(payloadJson))
                .execute();
    }

    private org.jooq.SelectJoinStep<?> findAnalysisSelect() {
        Field<String> evidenceText = JooqJson.jsonbText(RCA_ANALYSIS_EVIDENCE, "evidence_json");

        return dsl.select(
                RCA_ANALYSIS_ID,
                RCA_ANALYSIS_TENANT_ID,
                RCA_ANALYSIS_INCIDENT_ID,
                RCA_ANALYSIS_STATUS,
                RCA_ANALYSIS_SUSPECTED_ROOT_CAUSE,
                RCA_ANALYSIS_CONFIDENCE,
                RCA_ANALYSIS_SUMMARY,
                evidenceText,
                RCA_ANALYSIS_MODEL_VERSION,
                RCA_ANALYSIS_CREATED_AT
        ).from(RCA_ANALYSIS);
    }

    private RcaAnalysisRecord toAnalysis(org.jooq.Record record) {
        Field<String> evidenceText = JooqJson.jsonbText(RCA_ANALYSIS_EVIDENCE, "evidence_json");

        return new RcaAnalysisRecord(
                record.get(RCA_ANALYSIS_ID),
                record.get(RCA_ANALYSIS_TENANT_ID),
                record.get(RCA_ANALYSIS_INCIDENT_ID),
                record.get(RCA_ANALYSIS_STATUS),
                record.get(RCA_ANALYSIS_SUSPECTED_ROOT_CAUSE),
                record.get(RCA_ANALYSIS_CONFIDENCE),
                record.get(RCA_ANALYSIS_SUMMARY),
                record.get(evidenceText),
                record.get(RCA_ANALYSIS_MODEL_VERSION),
                record.get(RCA_ANALYSIS_CREATED_AT)
        );
    }
}
```

---

# 8. 替换 AI Repository 实现

## `modules/aiops-ai-client/src/main/java/io/aegisops/ai/client/JdbcAiRepository.java`

```java id="xn0lo4"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.*;
import io.aegisops.persistence.JooqJson;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static io.aegisops.persistence.AegisTables.*;
import static io.aegisops.persistence.JooqConditions.tenant;

@Repository
public class JdbcAiRepository implements AiRepository {
    private final DSLContext dsl;

    public JdbcAiRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
        return dsl.select(
                        INCIDENT_ID,
                        INCIDENT_TENANT_ID,
                        INCIDENT_TITLE,
                        INCIDENT_SUMMARY,
                        INCIDENT_SEVERITY,
                        INCIDENT_STATUS,
                        INCIDENT_SOURCE,
                        INCIDENT_PRIMARY_ASSET_ID,
                        INCIDENT_AGGREGATION_KEY,
                        INCIDENT_ALERT_COUNT,
                        INCIDENT_SUSPECTED_ROOT_CAUSE,
                        INCIDENT_CONFIDENCE,
                        INCIDENT_STARTED_AT,
                        INCIDENT_DETECTED_AT,
                        INCIDENT_LAST_SEEN_AT,
                        INCIDENT_CREATED_AT,
                        INCIDENT_UPDATED_AT
                )
                .from(INCIDENT)
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .fetchOptional(record -> new AiIncidentRecord(
                        record.get(INCIDENT_ID),
                        record.get(INCIDENT_TENANT_ID),
                        record.get(INCIDENT_TITLE),
                        record.get(INCIDENT_SUMMARY),
                        record.get(INCIDENT_SEVERITY),
                        record.get(INCIDENT_STATUS),
                        record.get(INCIDENT_SOURCE),
                        record.get(INCIDENT_PRIMARY_ASSET_ID),
                        record.get(INCIDENT_AGGREGATION_KEY),
                        record.get(INCIDENT_ALERT_COUNT) == null ? 0 : record.get(INCIDENT_ALERT_COUNT),
                        record.get(INCIDENT_SUSPECTED_ROOT_CAUSE),
                        record.get(INCIDENT_CONFIDENCE),
                        record.get(INCIDENT_STARTED_AT),
                        record.get(INCIDENT_DETECTED_AT),
                        record.get(INCIDENT_LAST_SEEN_AT),
                        record.get(INCIDENT_CREATED_AT),
                        record.get(INCIDENT_UPDATED_AT)
                ));
    }

    @Override
    public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        Field<String> labelsText = JooqJson.jsonbText(ALERT_EVENT_LABELS, "labels_json");

        return dsl.select(
                        ALERT_EVENT_ID,
                        ALERT_EVENT_SOURCE,
                        ALERT_EVENT_SOURCE_EVENT_ID,
                        ALERT_EVENT_SEVERITY,
                        ALERT_EVENT_TITLE,
                        ALERT_EVENT_DESCRIPTION,
                        ALERT_EVENT_ASSET_ID,
                        ALERT_EVENT_ENTITY_TYPE,
                        ALERT_EVENT_ENTITY_NAME,
                        ALERT_EVENT_FINGERPRINT,
                        labelsText,
                        ALERT_EVENT_STARTS_AT
                )
                .from(INCIDENT_EVENT)
                .join(INCIDENT).on(INCIDENT_ID.eq(INCIDENT_EVENT_INCIDENT_ID))
                .join(ALERT_EVENT).on(ALERT_EVENT_ID.eq(INCIDENT_EVENT_EVENT_ID))
                .where(tenant(INCIDENT_TENANT_ID, tenantId))
                .and(INCIDENT_ID.eq(incidentId))
                .and(INCIDENT_EVENT_EVENT_TYPE.eq("alert"))
                .and(tenant(ALERT_EVENT_TENANT_ID, tenantId))
                .orderBy(ALERT_EVENT_STARTS_AT.asc())
                .fetch(record -> new AiAlertRecord(
                        record.get(ALERT_EVENT_ID),
                        record.get(ALERT_EVENT_SOURCE),
                        record.get(ALERT_EVENT_SOURCE_EVENT_ID),
                        record.get(ALERT_EVENT_SEVERITY),
                        record.get(ALERT_EVENT_TITLE),
                        record.get(ALERT_EVENT_DESCRIPTION),
                        record.get(ALERT_EVENT_ASSET_ID),
                        record.get(ALERT_EVENT_ENTITY_TYPE),
                        record.get(ALERT_EVENT_ENTITY_NAME),
                        record.get(ALERT_EVENT_FINGERPRINT),
                        record.get(labelsText),
                        record.get(ALERT_EVENT_STARTS_AT)
                ));
    }

    @Override
    public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
        Field<String> evidenceText = JooqJson.jsonbText(RCA_ANALYSIS_EVIDENCE, "evidence_json");

        return dsl.select(
                        RCA_ANALYSIS_ID,
                        RCA_ANALYSIS_SUSPECTED_ROOT_CAUSE,
                        RCA_ANALYSIS_CONFIDENCE,
                        RCA_ANALYSIS_SUMMARY,
                        evidenceText,
                        RCA_ANALYSIS_MODEL_VERSION,
                        RCA_ANALYSIS_CREATED_AT
                )
                .from(RCA_ANALYSIS)
                .where(tenant(RCA_ANALYSIS_TENANT_ID, tenantId))
                .and(RCA_ANALYSIS_INCIDENT_ID.eq(incidentId))
                .orderBy(RCA_ANALYSIS_CREATED_AT.desc())
                .limit(1)
                .fetchOptional(record -> new AiRcaRecord(
                        record.get(RCA_ANALYSIS_ID),
                        record.get(RCA_ANALYSIS_SUSPECTED_ROOT_CAUSE),
                        record.get(RCA_ANALYSIS_CONFIDENCE),
                        record.get(RCA_ANALYSIS_SUMMARY),
                        record.get(evidenceText),
                        record.get(RCA_ANALYSIS_MODEL_VERSION),
                        record.get(RCA_ANALYSIS_CREATED_AT)
                ));
    }

    @Override
    public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
        return diagnosisSelect()
                .where(tenant(AI_DIAGNOSIS_TENANT_ID, tenantId))
                .and(AI_DIAGNOSIS_INCIDENT_ID.eq(incidentId))
                .orderBy(AI_DIAGNOSIS_CREATED_AT.desc())
                .limit(1)
                .fetchOptional(this::toDiagnosis);
    }

    @Override
    public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
        return diagnosisSelect()
                .where(tenant(AI_DIAGNOSIS_TENANT_ID, tenantId))
                .and(AI_DIAGNOSIS_ID.eq(diagnosisId))
                .fetchOptional(this::toDiagnosis);
    }

    @Override
    public void saveDiagnosis(
            String id,
            String tenantId,
            String incidentId,
            AgentDiagnosisResponse response,
            String requestJson,
            String rawJson,
            String nextStepsJson,
            String runbookSuggestionsJson,
            String risksJson
    ) {
        dsl.insertInto(AI_DIAGNOSIS)
                .set(AI_DIAGNOSIS_ID, id)
                .set(AI_DIAGNOSIS_TENANT_ID, tenantId)
                .set(AI_DIAGNOSIS_INCIDENT_ID, incidentId)
                .set(AI_DIAGNOSIS_STATUS, "completed")
                .set(AI_DIAGNOSIS_PROVIDER, response.provider())
                .set(AI_DIAGNOSIS_MODEL, response.model())
                .set(AI_DIAGNOSIS_AGENT_NAME, response.agentName())
                .set(AI_DIAGNOSIS_REQUEST_PAYLOAD, JooqJson.jsonb(requestJson))
                .set(AI_DIAGNOSIS_RESPONSE_RAW, JooqJson.jsonb(rawJson))
                .set(AI_DIAGNOSIS_SUMMARY, response.summary())
                .set(AI_DIAGNOSIS_ROOT_CAUSE, response.rootCause())
                .set(AI_DIAGNOSIS_IMPACT, response.impact())
                .set(AI_DIAGNOSIS_NEXT_STEPS, JooqJson.jsonbArray(nextStepsJson))
                .set(AI_DIAGNOSIS_RUNBOOK_SUGGESTIONS, JooqJson.jsonbArray(runbookSuggestionsJson))
                .set(AI_DIAGNOSIS_RISKS, JooqJson.jsonbArray(risksJson))
                .set(AI_DIAGNOSIS_CREATED_AT, OffsetDateTime.now())
                .execute();
    }

    @Override
    public void addIncidentTimeline(
            String id,
            String incidentId,
            OffsetDateTime eventTime,
            String title,
            String description,
            String payloadJson
    ) {
        dsl.insertInto(INCIDENT_TIMELINE)
                .set(INCIDENT_TIMELINE_ID, id)
                .set(INCIDENT_TIMELINE_INCIDENT_ID, incidentId)
                .set(INCIDENT_TIMELINE_EVENT_TIME, eventTime)
                .set(INCIDENT_TIMELINE_EVENT_TYPE, "ai_diagnosed")
                .set(INCIDENT_TIMELINE_TITLE, title)
                .set(INCIDENT_TIMELINE_DESCRIPTION, description)
                .set(INCIDENT_TIMELINE_SOURCE, "system")
                .set(INCIDENT_TIMELINE_PAYLOAD, JooqJson.jsonb(payloadJson))
                .execute();
    }

    private org.jooq.SelectJoinStep<?> diagnosisSelect() {
        Field<String> nextStepsText = JooqJson.jsonbText(AI_DIAGNOSIS_NEXT_STEPS, "next_steps_json");
        Field<String> runbookSuggestionsText = JooqJson.jsonbText(AI_DIAGNOSIS_RUNBOOK_SUGGESTIONS, "runbook_suggestions_json");
        Field<String> risksText = JooqJson.jsonbText(AI_DIAGNOSIS_RISKS, "risks_json");

        return dsl.select(
                AI_DIAGNOSIS_ID,
                AI_DIAGNOSIS_TENANT_ID,
                AI_DIAGNOSIS_INCIDENT_ID,
                AI_DIAGNOSIS_STATUS,
                AI_DIAGNOSIS_PROVIDER,
                AI_DIAGNOSIS_MODEL,
                AI_DIAGNOSIS_AGENT_NAME,
                AI_DIAGNOSIS_SUMMARY,
                AI_DIAGNOSIS_ROOT_CAUSE,
                AI_DIAGNOSIS_IMPACT,
                nextStepsText,
                runbookSuggestionsText,
                risksText,
                AI_DIAGNOSIS_CREATED_AT
        ).from(AI_DIAGNOSIS);
    }

    private AiDiagnosisRecord toDiagnosis(org.jooq.Record record) {
        Field<String> nextStepsText = JooqJson.jsonbText(AI_DIAGNOSIS_NEXT_STEPS, "next_steps_json");
        Field<String> runbookSuggestionsText = JooqJson.jsonbText(AI_DIAGNOSIS_RUNBOOK_SUGGESTIONS, "runbook_suggestions_json");
        Field<String> risksText = JooqJson.jsonbText(AI_DIAGNOSIS_RISKS, "risks_json");

        return new AiDiagnosisRecord(
                record.get(AI_DIAGNOSIS_ID),
                record.get(AI_DIAGNOSIS_TENANT_ID),
                record.get(AI_DIAGNOSIS_INCIDENT_ID),
                record.get(AI_DIAGNOSIS_STATUS),
                record.get(AI_DIAGNOSIS_PROVIDER),
                record.get(AI_DIAGNOSIS_MODEL),
                record.get(AI_DIAGNOSIS_AGENT_NAME),
                record.get(AI_DIAGNOSIS_SUMMARY),
                record.get(AI_DIAGNOSIS_ROOT_CAUSE),
                record.get(AI_DIAGNOSIS_IMPACT),
                record.get(nextStepsText),
                record.get(runbookSuggestionsText),
                record.get(risksText),
                record.get(AI_DIAGNOSIS_CREATED_AT)
        );
    }
}
```

---

# 9. 单元测试

## 9.1 `JooqJsonTest.java`

路径：

```txt id="5ejx7w"
modules/aiops-persistence/src/test/java/io/aegisops/persistence/JooqJsonTest.java
```

```java id="xq4boh"
package io.aegisops.persistence;

import org.jooq.JSONB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JooqJsonTest {
    @Test
    void jsonbDefaultsToObject() {
        JSONB value = JooqJson.jsonb(null);

        assertEquals("{}", value.data());
    }

    @Test
    void jsonbArrayDefaultsToArray() {
        JSONB value = JooqJson.jsonbArray(null);

        assertEquals("[]", value.data());
    }

    @Test
    void jsonbKeepsProvidedPayload() {
        JSONB value = JooqJson.jsonb("{\"ok\":true}");

        assertEquals("{\"ok\":true}", value.data());
    }
}
```

---

## 9.2 `JooqConditionsTest.java`

路径：

```txt id="xdznq0"
modules/aiops-persistence/src/test/java/io/aegisops/persistence/JooqConditionsTest.java
```

```java id="01casv"
package io.aegisops.persistence;

import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.aegisops.persistence.AegisTables.INCIDENT_TENANT_ID;
import static io.aegisops.persistence.AegisTables.ALERT_EVENT_ASSET_ID;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JooqConditionsTest {
    @Test
    void tenantReturnsFalseConditionWhenTenantMissing() {
        String sql = DSL.using(SQLDialect.POSTGRES)
                .renderInlined(JooqConditions.tenant(INCIDENT_TENANT_ID, null));

        assertTrue(sql.contains("false"));
    }

    @Test
    void tenantRendersEqualityWhenTenantProvided() {
        String sql = DSL.using(SQLDialect.POSTGRES)
                .renderInlined(JooqConditions.tenant(INCIDENT_TENANT_ID, "tenant_1"));

        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("tenant_1"));
    }

    @Test
    void inOrFalseFiltersEmptyValues() {
        String sql = DSL.using(SQLDialect.POSTGRES)
                .renderInlined(JooqConditions.inOrFalse(ALERT_EVENT_ASSET_ID, List.of("", "asset_1", "asset_1")));

        assertTrue(sql.contains("asset_id"));
        assertTrue(sql.contains("asset_1"));
    }
}
```

---

## 9.3 `JdbcAiRepositoryJooqTest.java`

路径：

```txt id="m9dj8j"
modules/aiops-ai-client/src/test/java/io/aegisops/ai/client/JdbcAiRepositoryJooqTest.java
```

```java id="jb6drz"
package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.using;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAiRepositoryJooqTest {
    @Test
    void findIncidentRendersTenantAndIncidentConditions() {
        CapturingProvider provider = new CapturingProvider();
        JdbcAiRepository repository = new JdbcAiRepository(dsl(provider));

        repository.findIncident("tenant_1", "inc_1");

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("from incident"));
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("id"));
    }

    @Test
    void listIncidentAlertsRendersJoins() {
        CapturingProvider provider = new CapturingProvider();
        JdbcAiRepository repository = new JdbcAiRepository(dsl(provider));

        repository.listIncidentAlerts("tenant_1", "inc_1");

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("join incident"));
        assertTrue(sql.contains("join alert_event"));
        assertTrue(sql.contains("event_type"));
    }

    @Test
    void saveDiagnosisRendersInsertIntoAiDiagnosis() {
        CapturingProvider provider = new CapturingProvider();
        JdbcAiRepository repository = new JdbcAiRepository(dsl(provider));

        repository.saveDiagnosis(
                "diag_1",
                "tenant_1",
                "inc_1",
                new AgentDiagnosisResponse(
                        "aiops-agent",
                        "langgraph-deterministic",
                        "aegisops_diagnosis_graph",
                        "summary",
                        "root",
                        "impact",
                        List.of("step"),
                        List.of("runbook"),
                        List.of("risk"),
                        Map.of("ok", true)
                ),
                "{}",
                "{}",
                "[\"step\"]",
                "[\"runbook\"]",
                "[\"risk\"]"
        );

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("insert into ai_diagnosis"));
        assertTrue(sql.contains("request_payload"));
        assertTrue(sql.contains("response_raw"));
    }

    private DSLContext dsl(MockDataProvider provider) {
        return using(new MockConnection(provider), SQLDialect.POSTGRES);
    }

    private static final class CapturingProvider implements MockDataProvider {
        private final List<String> sql = new ArrayList<>();

        @Override
        public MockResult[] execute(org.jooq.tools.jdbc.MockExecuteContext ctx) {
            sql.add(ctx.sql());
            return new MockResult[] { new MockResult(0, using(SQLDialect.POSTGRES).newResult()) };
        }

        List<String> sql() {
            return sql;
        }
    }
}
```

---

## 9.4 `JdbcRcaRepositoryJooqTest.java`

路径：

```txt id="r3wrgu"
modules/aiops-rca/src/test/java/io/aegisops/rca/JdbcRcaRepositoryJooqTest.java
```

```java id="rx38bg"
package io.aegisops.rca;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.using;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRcaRepositoryJooqTest {
    @Test
    void findLatestAnalysisRendersRcaQuery() {
        CapturingProvider provider = new CapturingProvider();
        JdbcRcaRepository repository = new JdbcRcaRepository(dsl(provider));

        repository.findLatestAnalysis("tenant_1", "inc_1");

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("from rca_analysis"));
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("incident_id"));
    }

    @Test
    void listAssetRelationsRendersInCondition() {
        CapturingProvider provider = new CapturingProvider();
        JdbcRcaRepository repository = new JdbcRcaRepository(dsl(provider));

        repository.listAssetRelations("tenant_1", List.of("asset_1", "asset_2"));

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("from asset_relation"));
        assertTrue(sql.contains("from_asset_id"));
        assertTrue(sql.contains("to_asset_id"));
    }

    @Test
    void saveAnalysisRendersInsertIntoRcaAnalysis() {
        CapturingProvider provider = new CapturingProvider();
        JdbcRcaRepository repository = new JdbcRcaRepository(dsl(provider));

        repository.saveAnalysis(
                "rca_1",
                "tenant_1",
                "inc_1",
                "root",
                new BigDecimal("0.8000"),
                "summary",
                "[]",
                "rules-v1"
        );

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("insert into rca_analysis"));
        assertTrue(sql.contains("suspected_root_cause"));
        assertTrue(sql.contains("evidence"));
    }

    private DSLContext dsl(MockDataProvider provider) {
        return using(new MockConnection(provider), SQLDialect.POSTGRES);
    }

    private static final class CapturingProvider implements MockDataProvider {
        private final List<String> sql = new ArrayList<>();

        @Override
        public MockResult[] execute(org.jooq.tools.jdbc.MockExecuteContext ctx) {
            sql.add(ctx.sql());
            return new MockResult[] { new MockResult(0, using(SQLDialect.POSTGRES).newResult()) };
        }

        List<String> sql() {
            return sql;
        }
    }
}
```

---

## 9.5 `JdbcIncidentRepositoryJooqTest.java`

路径：

```txt id="q0fk9j"
modules/aiops-incident/src/test/java/io/aegisops/incident/JdbcIncidentRepositoryJooqTest.java
```

```java id="6m9e3d"
package io.aegisops.incident;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.jooq.impl.DSL.using;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcIncidentRepositoryJooqTest {
    @Test
    void findOpenAlertCandidatesRendersNotExists() {
        CapturingProvider provider = new CapturingProvider();
        JdbcIncidentRepository repository = new JdbcIncidentRepository(dsl(provider));

        repository.findOpenAlertCandidates("tenant_1", OffsetDateTime.now().minusHours(1), 100);

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("from alert_event"));
        assertTrue(sql.contains("not exists"));
        assertTrue(sql.contains("incident_event"));
    }

    @Test
    void findActiveIncidentRendersStatusFilter() {
        CapturingProvider provider = new CapturingProvider();
        JdbcIncidentRepository repository = new JdbcIncidentRepository(dsl(provider));

        repository.findActiveIncidentByAggregationKey("tenant_1", "zabbix:fp_cpu");

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("from incident"));
        assertTrue(sql.contains("aggregation_key"));
        assertTrue(sql.contains("status"));
    }

    @Test
    void linkAlertRendersOnConflictDoNothing() {
        CapturingProvider provider = new CapturingProvider();
        JdbcIncidentRepository repository = new JdbcIncidentRepository(dsl(provider));

        repository.linkAlert("ie_1", "inc_1", "alert_1", "primary", OffsetDateTime.now());

        String sql = provider.sql().get(0);
        assertTrue(sql.contains("insert into incident_event"));
        assertTrue(sql.contains("on conflict do nothing"));
    }

    private DSLContext dsl(MockDataProvider provider) {
        return using(new MockConnection(provider), SQLDialect.POSTGRES);
    }

    private static final class CapturingProvider implements MockDataProvider {
        private final List<String> sql = new ArrayList<>();

        @Override
        public MockResult[] execute(org.jooq.tools.jdbc.MockExecuteContext ctx) {
            sql.add(ctx.sql());
            return new MockResult[] { new MockResult(0, using(SQLDialect.POSTGRES).newResult()) };
        }

        List<String> sql() {
            return sql;
        }
    }
}
```

---

# 10. 配置建议

`apps/aiops-server/src/main/resources/application.yml` 或当前配置文件里增加：

```yaml id="sfg63b"
spring:
  jooq:
    sql-dialect: postgres
```

如果是 `.properties`：

```properties id="2ifkfl"
spring.jooq.sql-dialect=postgres
```

Spring Boot 可以自动配置 `DSLContext`，但显式声明 dialect 能减少环境自动探测误差。([Home][1])

---

# 11. 验证命令

## 11.1 单模块测试

```powershell id="bk2lih"
mvn -pl modules/aiops-persistence -am test
mvn -pl modules/aiops-incident -am test
mvn -pl modules/aiops-rca -am test
mvn -pl modules/aiops-ai-client -am test
```

## 11.2 Server 测试

```powershell id="tkr6yf"
mvn -pl apps/aiops-server -am test
```

## 11.3 全量测试

```powershell id="qg0sh2"
mvn test
```

---

# 12. Phase4.5 验收标准

```txt id="mc1103"
1. aiops-persistence 模块可以独立测试通过
2. aiops-incident 测试通过
3. aiops-rca 测试通过
4. aiops-ai-client 测试通过
5. apps/aiops-server 启动正常
6. Sync Zabbix 正常
7. Aggregate Incidents 正常
8. Analyze RCA 正常
9. AI Diagnose 正常
10. ai_diagnosis / incident_timeline 正常写入
```

---

# 13. 后续演进

Phase4.5 当前是：

```txt id="habjui"
jOOQ DSL no-codegen
```

后续可以做 Phase4.6：

```txt id="w1rxzr"
jOOQ Codegen + Flyway schema source
```

Phase4.6 再把：

```txt id="w5aevm"
AegisTables.INCIDENT_ID
```

升级为：

```txt id="hz6jse"
Tables.INCIDENT.ID
```

那时就能获得更完整的 schema 级类型安全。当前 Phase4.5 的价值是先把复杂 SQL 从业务代码中抽离出来，并避免继续扩大 `JdbcTemplate + SQL 字符串` 的维护成本。

[1]: https://docs.spring.io/spring-boot/reference/data/sql.html "SQL Databases :: Spring Boot"
[2]: https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/ "The DSLContext API"
[3]: https://www.jooq.org/doc/latest/manual/code-generation/codegen-maven/ "Running the code generator with Maven"
