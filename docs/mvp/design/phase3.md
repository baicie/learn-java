---
title: Phase3：RCA 规则引擎与证据链
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---

# Phase3：RCA 规则引擎与证据链

当前仓库已经具备 Phase0/Phase1/Phase2 的基础：根 `pom.xml` 已经是 Maven 多模块，并包含 `aiops-incident`，server 也已经依赖 `aiops-incident`；`incident` 表里也已经预留了 `suspected_root_cause` 与 `confidence` 字段。

Phase2 目前已经能把 `alert_event` 聚合成 `incident`，并提供事故详情、状态流转、告警关联与时间线。

所以 Phase3 不应该再改 Incident 聚合，而是在 Incident 之上新增一个独立能力：

```txt
Incident -> RCA Rule Engine -> Evidence Chain -> suspected_root_cause/confidence -> Timeline
```

---

# 1. Phase3 目标

本阶段完成：

```txt
新增 aiops-rca 模块
新增 rca_analysis 表
实现 RCA 规则引擎
实现 6 条 MVP 规则
实现 Evidence Chain
实现 RCA 分析 API
分析结果写回 incident.suspected_root_cause / incident.confidence
分析动作写入 incident_timeline
前端 Incident Detail 增加 Analyze RCA 按钮与结果展示
完整单元测试
```

本阶段不做：

```txt
LLM 总结
真实指标查询 VictoriaMetrics
日志查询 ClickHouse
拓扑图可视化
Runbook 推荐
Ansible 执行
```

这些放到 Phase4/Phase5。

---

# 2. Phase3 规则设计

Phase3 先做规则型 RCA，不接 LLM。规则包括：

```txt
R1_HIGH_SEVERITY
高严重级别告警优先作为根因候选。

R2_ALERT_VOLUME
短时间内同 Incident 下大量告警，判断为告警风暴/集中爆发。

R3_SAME_FINGERPRINT
多个告警具有相同 fingerprint，判断为同一触发器/同一故障模式。

R4_SAME_ASSET_CONCENTRATION
多个告警集中在同一 asset，判断为该资产局部故障。

R5_DEPENDENCY_RELATION
Incident 关联资产之间存在 depends_on/calls 等依赖关系，判断可能是上游影响下游。

R6_TIMELINE_BURST
告警在短窗口内集中发生，判断可能是一次突发变更、资源抖动或依赖异常。
```

RCA 输出：

```txt
suspectedRootCause: 最可能根因
confidence: 0 ~ 0.99
summary: 简要说明
evidence: 证据链数组
```

---

# 3. 文件清单

## 3.1 修改文件

```txt
pom.xml
apps/aiops-server/pom.xml
web/console/src/api/client.ts
web/console/src/pages/DashboardPage.tsx
web/console/src/components/console/incident-detail-card.tsx
```

> 说明：RCA 按钮与结果展示的 React 组件并没有塞回 `DashboardPage.tsx`，
> 而是落在 `incident-detail-card.tsx` 中由 `DashboardPage` 透传
> `rcaResult` / `isAnalyzing` / `onAnalyze` props，遵循关注点分离。

## 3.2 新增文件

```txt
apps/aiops-server/src/main/resources/db/migration/V5__phase3_rca_analysis.sql

modules/aiops-rca/pom.xml

modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAlertRecord.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisContext.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisRecord.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisResponse.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisResult.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalyzeRequest.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAssetRelationRecord.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaController.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaEngine.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaEvidence.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaIncidentRecord.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRepository.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRule.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRuleResult.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRows.java
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaService.java
modules/aiops-rca/src/main/java/io/aegisops/rca/JdbcRcaRepository.java

modules/aiops-rca/src/main/java/io/aegisops/rca/rules/HighSeverityRcaRule.java
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/AlertVolumeRcaRule.java
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/SameFingerprintRcaRule.java
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/SameAssetConcentrationRcaRule.java
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/DependencyRelationRcaRule.java
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/TimelineBurstRcaRule.java

modules/aiops-rca/src/test/java/io/aegisops/rca/RcaTestFixtures.java
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaEngineTest.java
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaServiceTest.java
modules/aiops-rca/src/test/java/io/aegisops/rca/rules/RcaRulesTest.java
```

---

# 4. Maven 配置

## 4.1 替换根 `pom.xml`

路径：

```txt
pom.xml
```

完整内容：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.aegisops</groupId>
    <artifactId>aegisops</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>AegisOps</name>
    <description>AI Ops incident diagnosis and automation platform</description>

    <modules>
        <module>modules/aiops-common</module>
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
        <module>apps/aiops-server</module>
        <module>apps/aiops-worker</module>
        <module>apps/aiops-runner</module>
    </modules>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.5.8</spring-boot.version>
        <springdoc.version>2.8.14</springdoc.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>${java.version}</maven.compiler.release>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <release>${java.version}</release>
                        <parameters>true</parameters>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

---

## 4.2 替换 `apps/aiops-server/pom.xml`

路径：

```txt
apps/aiops-server/pom.xml
```

完整内容：

```xml
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

    <artifactId>aiops-server</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-common</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-web</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-security</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-tenant</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-user</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-audit</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-datasource</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-asset</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-alert</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-incident</artifactId><version>${project.version}</version></dependency>
        <dependency><groupId>io.aegisops</groupId><artifactId>aiops-rca</artifactId><version>${project.version}</version></dependency>

        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <mainClass>io.aegisops.server.AiOpsServerApplication</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4.3 新增 `modules/aiops-rca/pom.xml`

```xml
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

    <artifactId>aiops-rca</artifactId>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>io.aegisops</groupId>
            <artifactId>aiops-common</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

# 5. 数据库迁移

> 迁移文件编号为 `V5` 而非 `V4`：`V4__phase2_incident_aggregation.sql` 已被
> Phase2 占用，所以 Phase3 顺延为 `V5__phase3_rca_analysis.sql`。
> 这条规则由 Flyway 按字母序递增版本号强制保证。

## `apps/aiops-server/src/main/resources/db/migration/V5__phase3_rca_analysis.sql`

```sql
create table if not exists rca_analysis (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id),
  incident_id varchar(64) not null references incident(id) on delete cascade,
  status varchar(32) not null default 'completed',
  suspected_root_cause text not null,
  confidence numeric(5,4) not null default 0,
  summary text,
  evidence jsonb not null default '[]'::jsonb,
  model_version varchar(64) not null default 'rules-v1',
  created_at timestamptz not null default now()
);

create index if not exists idx_rca_analysis_tenant_incident_created
  on rca_analysis(tenant_id, incident_id, created_at desc);

create index if not exists idx_rca_analysis_incident
  on rca_analysis(incident_id);
```

---

# 6. 后端 RCA 模块代码

## `RcaIncidentRecord.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaIncidentRecord.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RcaIncidentRecord(
        String id,
        String tenantId,
        String title,
        String summary,
        String severity,
        String status,
        String source,
        String primaryAssetId,
        String aggregationKey,
        int alertCount,
        String suspectedRootCause,
        BigDecimal confidence,
        OffsetDateTime startedAt,
        OffsetDateTime detectedAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
```

---

## `RcaAlertRecord.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAlertRecord.java
```

```java
package io.aegisops.rca;

import java.time.OffsetDateTime;

public record RcaAlertRecord(
        String id,
        String source,
        String sourceEventId,
        String severity,
        String title,
        String description,
        String assetId,
        String entityType,
        String entityName,
        String fingerprint,
        String labelsJson,
        OffsetDateTime startsAt,
        OffsetDateTime createdAt
) {}
```

---

## `RcaAssetRelationRecord.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAssetRelationRecord.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;

public record RcaAssetRelationRecord(
        String id,
        String fromAssetId,
        String toAssetId,
        String relationType,
        BigDecimal confidence,
        String source
) {}
```

---

## `RcaEvidence.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaEvidence.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.Map;

public record RcaEvidence(
        String ruleId,
        String title,
        String description,
        BigDecimal score,
        BigDecimal confidence,
        Map<String, Object> attributes
) {}
```

---

## `RcaAnalysisContext.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisContext.java
```

```java
package io.aegisops.rca;

import java.util.List;

public record RcaAnalysisContext(
        RcaIncidentRecord incident,
        List<RcaAlertRecord> alerts,
        List<RcaAssetRelationRecord> assetRelations
) {}
```

---

## `RcaRuleResult.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRuleResult.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;

public record RcaRuleResult(
        String ruleId,
        String suspectedRootCause,
        BigDecimal score,
        BigDecimal confidence,
        List<RcaEvidence> evidence
) {
    public static RcaRuleResult none(String ruleId) {
        return new RcaRuleResult(ruleId, "", BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    public boolean matched() {
        return score.compareTo(BigDecimal.ZERO) > 0 && !evidence.isEmpty();
    }
}
```

---

## `RcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRule.java
```

```java
package io.aegisops.rca;

public interface RcaRule {
    String id();

    RcaRuleResult evaluate(RcaAnalysisContext context);
}
```

---

## `RcaAnalysisResult.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisResult.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.List;

public record RcaAnalysisResult(
        String suspectedRootCause,
        BigDecimal confidence,
        String summary,
        List<RcaEvidence> evidence
) {}
```

---

## `RcaAnalysisRecord.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisRecord.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record RcaAnalysisRecord(
        String id,
        String tenantId,
        String incidentId,
        String status,
        String suspectedRootCause,
        BigDecimal confidence,
        String summary,
        String evidenceJson,
        String modelVersion,
        OffsetDateTime createdAt
) {}
```

---

## `RcaAnalysisResponse.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalysisResponse.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record RcaAnalysisResponse(
        String id,
        String incidentId,
        String status,
        String suspectedRootCause,
        BigDecimal confidence,
        String summary,
        List<RcaEvidence> evidence,
        String modelVersion,
        OffsetDateTime createdAt
) {}
```

---

## `RcaAnalyzeRequest.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaAnalyzeRequest.java
```

```java
package io.aegisops.rca;

public record RcaAnalyzeRequest(
        Boolean force
) {
    public boolean forceEnabled() {
        return Boolean.TRUE.equals(force);
    }
}
```

---

## `RcaRepository.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRepository.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RcaRepository {
    Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId);

    List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

    List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds);

    Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId);

    void saveAnalysis(
            String id,
            String tenantId,
            String incidentId,
            String suspectedRootCause,
            BigDecimal confidence,
            String summary,
            String evidenceJson,
            String modelVersion
    );

    Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id);

    void updateIncidentRca(String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence);

    void addIncidentTimeline(
            String id,
            String incidentId,
            OffsetDateTime eventTime,
            String title,
            String description,
            String payloadJson
    );
}
```

---

## `RcaRows.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaRows.java
```

```java
package io.aegisops.rca;

import java.sql.ResultSet;
import java.sql.SQLException;

final class RcaRows {
    private RcaRows() {}

    static RcaIncidentRecord incident(ResultSet rs) throws SQLException {
        return new RcaIncidentRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("title"),
                rs.getString("summary"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getString("primary_asset_id"),
                rs.getString("aggregation_key"),
                rs.getInt("alert_count"),
                rs.getString("suspected_root_cause"),
                rs.getBigDecimal("confidence"),
                rs.getObject("started_at", java.time.OffsetDateTime.class),
                rs.getObject("detected_at", java.time.OffsetDateTime.class),
                rs.getObject("last_seen_at", java.time.OffsetDateTime.class),
                rs.getObject("resolved_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }

    static RcaAnalysisRecord analysis(ResultSet rs) throws SQLException {
        return new RcaAnalysisRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("incident_id"),
                rs.getString("status"),
                rs.getString("suspected_root_cause"),
                rs.getBigDecimal("confidence"),
                rs.getString("summary"),
                rs.getString("evidence"),
                rs.getString("model_version"),
                rs.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
```

---

## `JdbcRcaRepository.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/JdbcRcaRepository.java
```

```java
package io.aegisops.rca;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRcaRepository implements RcaRepository {
    private final JdbcTemplate jdbc;

    public JdbcRcaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, suspected_root_cause, confidence,
                           started_at, detected_at, last_seen_at, resolved_at, created_at, updated_at
                    from incident
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> RcaRows.incident(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
        return jdbc.query("""
                select a.id, a.source, a.source_event_id, a.severity, a.title, a.description,
                       a.asset_id, a.entity_type, a.entity_name, a.fingerprint, a.labels::text,
                       a.starts_at, a.created_at
                from incident_event ie
                join incident i on i.id = ie.incident_id
                join alert_event a on a.id = ie.event_id
                where i.tenant_id = ?
                  and i.id = ?
                  and ie.event_type = 'alert'
                  and a.tenant_id = ?
                order by a.starts_at asc
                """, (rs, rowNum) -> new RcaAlertRecord(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getString("labels"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)
        ), tenantId, incidentId, tenantId);
    }

    @Override
    public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return List.of();
        }

        return jdbc.query("""
                select id, from_asset_id, to_asset_id, relation_type, confidence, source
                from asset_relation
                where tenant_id = ?
                  and (
                    from_asset_id = any(?)
                    or to_asset_id = any(?)
                  )
                order by confidence desc
                """, ps -> {
            ps.setString(1, tenantId);
            ps.setArray(2, ps.getConnection().createArrayOf("varchar", assetIds.toArray()));
            ps.setArray(3, ps.getConnection().createArrayOf("varchar", assetIds.toArray()));
        }, (rs, rowNum) -> new RcaAssetRelationRecord(
                rs.getString("id"),
                rs.getString("from_asset_id"),
                rs.getString("to_asset_id"),
                rs.getString("relation_type"),
                rs.getBigDecimal("confidence"),
                rs.getString("source")
        ));
    }

    @Override
    public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, incident_id, status, suspected_root_cause, confidence,
                           summary, evidence::text, model_version, created_at
                    from rca_analysis
                    where tenant_id = ? and incident_id = ?
                    order by created_at desc
                    limit 1
                    """, (rs, rowNum) -> RcaRows.analysis(rs), tenantId, incidentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
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
        jdbc.update("""
                insert into rca_analysis(id, tenant_id, incident_id, status, suspected_root_cause,
                                         confidence, summary, evidence, model_version, created_at)
                values (?, ?, ?, 'completed', ?, ?, ?, ?::jsonb, ?, now())
                """,
                id,
                tenantId,
                incidentId,
                suspectedRootCause,
                confidence,
                summary,
                evidenceJson,
                modelVersion
        );
    }

    @Override
    public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    select id, tenant_id, incident_id, status, suspected_root_cause, confidence,
                           summary, evidence::text, model_version, created_at
                    from rca_analysis
                    where tenant_id = ? and id = ?
                    """, (rs, rowNum) -> RcaRows.analysis(rs), tenantId, id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void updateIncidentRca(String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
        jdbc.update("""
                update incident
                set suspected_root_cause = ?,
                    confidence = ?,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """, suspectedRootCause, confidence, tenantId, incidentId);
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
        jdbc.update("""
                insert into incident_timeline(id, incident_id, event_time, event_type, title, description, source, payload)
                values (?, ?, ?, 'rca_analyzed', ?, ?, 'system', ?::jsonb)
                """, id, incidentId, eventTime, title, description, payloadJson);
    }
}
```

---

## `RcaEngine.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaEngine.java
```

```java
package io.aegisops.rca;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Component
public class RcaEngine {
    private final List<RcaRule> rules;

    public RcaEngine(List<RcaRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(RcaRule::id))
                .toList();
    }

    public RcaAnalysisResult analyze(RcaAnalysisContext context) {
        List<RcaRuleResult> matched = rules.stream()
                .map(rule -> rule.evaluate(context))
                .filter(RcaRuleResult::matched)
                .sorted(Comparator.comparing(RcaRuleResult::score).reversed())
                .toList();

        if (matched.isEmpty()) {
            return new RcaAnalysisResult(
                    "No strong root-cause signal found",
                    new BigDecimal("0.10"),
                    "No RCA rule produced enough evidence. Keep collecting metrics, logs, changes, and topology data.",
                    List.of()
            );
        }

        RcaRuleResult top = matched.get(0);

        List<RcaEvidence> evidence = matched.stream()
                .flatMap(result -> result.evidence().stream())
                .toList();

        BigDecimal confidence = matched.stream()
                .map(result -> result.confidence().multiply(normalizeScore(result.score())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .min(new BigDecimal("0.99"))
                .setScale(4, RoundingMode.HALF_UP);

        String summary = "RCA matched " + matched.size()
                + " rule(s), collected " + evidence.size()
                + " evidence item(s). Top rule: " + top.ruleId() + ".";

        return new RcaAnalysisResult(
                top.suspectedRootCause(),
                confidence,
                summary,
                evidence
        );
    }

    private BigDecimal normalizeScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return score.min(new BigDecimal("1.00"));
    }
}
```

---

## `RcaService.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaService.java
```

```java
package io.aegisops.rca;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RcaService {
    private static final String MODEL_VERSION = "rules-v1";

    private final RcaRepository repository;
    private final RcaEngine engine;
    private final ObjectMapper objectMapper;

    public RcaService(RcaRepository repository, RcaEngine engine, ObjectMapper objectMapper) {
        this.repository = repository;
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    public RcaAnalysisResponse latest(String tenantId, String incidentId) {
        ensureIncidentExists(tenantId, incidentId);

        return repository.findLatestAnalysis(tenantId, incidentId)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException("RCA_NOT_FOUND", "RCA analysis not found"));
    }

    @Transactional
    public RcaAnalysisResponse analyze(String tenantId, String incidentId, RcaAnalyzeRequest request) {
        RcaAnalyzeRequest normalizedRequest = request == null ? new RcaAnalyzeRequest(false) : request;

        if (!normalizedRequest.forceEnabled()) {
            var latest = repository.findLatestAnalysis(tenantId, incidentId);
            if (latest.isPresent()) {
                return toResponse(latest.get());
            }
        }

        RcaIncidentRecord incident = repository.findIncident(tenantId, incidentId)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

        List<RcaAlertRecord> alerts = repository.listIncidentAlerts(tenantId, incidentId);
        List<String> assetIds = alerts.stream()
                .map(RcaAlertRecord::assetId)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        List<RcaAssetRelationRecord> relations = repository.listAssetRelations(tenantId, assetIds);
        RcaAnalysisResult result = engine.analyze(new RcaAnalysisContext(incident, alerts, relations));

        String id = newId("rca");
        String evidenceJson = writeJson(result.evidence());

        repository.saveAnalysis(
                id,
                tenantId,
                incidentId,
                result.suspectedRootCause(),
                clampConfidence(result.confidence()),
                result.summary(),
                evidenceJson,
                MODEL_VERSION
        );

        repository.updateIncidentRca(
                tenantId,
                incidentId,
                result.suspectedRootCause(),
                clampConfidence(result.confidence())
        );

        repository.addIncidentTimeline(
                newId("tl"),
                incidentId,
                OffsetDateTime.now(),
                "RCA analysis completed",
                result.summary(),
                """
                {
                  "rcaAnalysisId": "%s",
                  "suspectedRootCause": "%s",
                  "confidence": "%s"
                }
                """.formatted(
                        escapeJson(id),
                        escapeJson(result.suspectedRootCause()),
                        clampConfidence(result.confidence()).toPlainString()
                )
        );

        return repository.findAnalysis(tenantId, id)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException("RCA_NOT_FOUND", "RCA analysis not found after save"));
    }

    private void ensureIncidentExists(String tenantId, String incidentId) {
        if (repository.findIncident(tenantId, incidentId).isEmpty()) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
    }

    private RcaAnalysisResponse toResponse(RcaAnalysisRecord record) {
        return new RcaAnalysisResponse(
                record.id(),
                record.incidentId(),
                record.status(),
                record.suspectedRootCause(),
                record.confidence(),
                record.summary(),
                readEvidence(record.evidenceJson()),
                record.modelVersion(),
                record.createdAt()
        );
    }

    private List<RcaEvidence> readEvidence(String evidenceJson) {
        try {
            if (evidenceJson == null || evidenceJson.isBlank()) {
                return List.of();
            }

            return objectMapper.readValue(evidenceJson, new TypeReference<List<RcaEvidence>>() {});
        } catch (Exception ex) {
            throw new AppException("RCA_EVIDENCE_INVALID", "RCA evidence is invalid");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AppException("RCA_EVIDENCE_INVALID", "Failed to serialize RCA evidence");
        }
    }

    private BigDecimal clampConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return BigDecimal.ZERO;
        }

        if (confidence.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }

        if (confidence.compareTo(new BigDecimal("0.99")) > 0) {
            return new BigDecimal("0.99");
        }

        return confidence;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
```

---

## `RcaController.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/RcaController.java
```

```java
package io.aegisops.rca;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents/{incidentId}/rca")
public class RcaController {
    private final RcaService rcaService;

    public RcaController(RcaService rcaService) {
        this.rcaService = rcaService;
    }

    @GetMapping("/latest")
    public ApiResponse<RcaAnalysisResponse> latest(@PathVariable String incidentId) {
        return ApiResponse.ok(rcaService.latest(TenantContext.requireTenantId(), incidentId));
    }

    @PostMapping("/analyze")
    public ApiResponse<RcaAnalysisResponse> analyze(
            @PathVariable String incidentId,
            @RequestBody(required = false) RcaAnalyzeRequest request
    ) {
        return ApiResponse.ok(rcaService.analyze(TenantContext.requireTenantId(), incidentId, request));
    }
}
```

---

# 7. RCA 规则代码

## `HighSeverityRcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/HighSeverityRcaRule.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class HighSeverityRcaRule implements RcaRule {
    @Override
    public String id() {
        return "R1_HIGH_SEVERITY";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        var topAlert = context.alerts().stream()
                .max(Comparator.comparingInt(alert -> weight(alert.severity())))
                .orElse(null);

        if (topAlert == null || weight(topAlert.severity()) < 40) {
            return RcaRuleResult.none(id());
        }

        BigDecimal score = "disaster".equalsIgnoreCase(topAlert.severity())
                ? new BigDecimal("0.90")
                : new BigDecimal("0.75");

        return new RcaRuleResult(
                id(),
                "High severity alert indicates the primary fault domain: " + topAlert.title(),
                score,
                new BigDecimal("0.72"),
                List.of(new RcaEvidence(
                        id(),
                        "High severity alert detected",
                        "The incident contains a " + topAlert.severity() + " alert: " + topAlert.title(),
                        score,
                        new BigDecimal("0.72"),
                        Map.of(
                                "alertId", topAlert.id(),
                                "severity", topAlert.severity(),
                                "title", topAlert.title(),
                                "assetId", topAlert.assetId() == null ? "" : topAlert.assetId()
                        )
                ))
        );
    }

    private int weight(String severity) {
        if (severity == null) {
            return 10;
        }

        return switch (severity.toLowerCase()) {
            case "disaster" -> 50;
            case "critical" -> 40;
            case "warning" -> 30;
            case "low" -> 20;
            default -> 10;
        };
    }
}
```

---

## `AlertVolumeRcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/AlertVolumeRcaRule.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class AlertVolumeRcaRule implements RcaRule {
    @Override
    public String id() {
        return "R2_ALERT_VOLUME";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        int count = context.alerts().size();

        if (count < 3) {
            return RcaRuleResult.none(id());
        }

        BigDecimal score = count >= 10 ? new BigDecimal("0.85") : new BigDecimal("0.62");
        BigDecimal confidence = count >= 10 ? new BigDecimal("0.70") : new BigDecimal("0.55");

        return new RcaRuleResult(
                id(),
                "Multiple alerts were triggered together, suggesting an alert storm or shared dependency failure",
                score,
                confidence,
                List.of(new RcaEvidence(
                        id(),
                        "Alert volume is abnormal",
                        "The incident contains " + count + " linked alerts.",
                        score,
                        confidence,
                        Map.of("alertCount", count)
                ))
        );
    }
}
```

---

## `SameFingerprintRcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/SameFingerprintRcaRule.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SameFingerprintRcaRule implements RcaRule {
    @Override
    public String id() {
        return "R3_SAME_FINGERPRINT";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        Map<String, List<RcaAlertRecord>> groups = context.alerts().stream()
                .filter(alert -> alert.fingerprint() != null && !alert.fingerprint().isBlank())
                .collect(Collectors.groupingBy(RcaAlertRecord::fingerprint));

        var top = groups.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .orElse(null);

        if (top == null || top.getValue().size() < 2) {
            return RcaRuleResult.none(id());
        }

        int count = top.getValue().size();
        BigDecimal score = count >= 5 ? new BigDecimal("0.88") : new BigDecimal("0.70");
        BigDecimal confidence = count >= 5 ? new BigDecimal("0.76") : new BigDecimal("0.62");

        return new RcaRuleResult(
                id(),
                "Repeated alerts share the same fingerprint, suggesting the same trigger or failure mode",
                score,
                confidence,
                List.of(new RcaEvidence(
                        id(),
                        "Same fingerprint repeated",
                        count + " alerts share fingerprint " + top.getKey(),
                        score,
                        confidence,
                        Map.of(
                                "fingerprint", top.getKey(),
                                "alertCount", count
                        )
                ))
        );
    }
}
```

---

## `SameAssetConcentrationRcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/SameAssetConcentrationRcaRule.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SameAssetConcentrationRcaRule implements RcaRule {
    @Override
    public String id() {
        return "R4_SAME_ASSET_CONCENTRATION";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        Map<String, List<RcaAlertRecord>> groups = context.alerts().stream()
                .filter(alert -> alert.assetId() != null && !alert.assetId().isBlank())
                .collect(Collectors.groupingBy(RcaAlertRecord::assetId));

        var top = groups.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .orElse(null);

        if (top == null || top.getValue().size() < 2) {
            return RcaRuleResult.none(id());
        }

        int alertCount = context.alerts().size();
        int assetAlertCount = top.getValue().size();
        double ratio = alertCount == 0 ? 0 : (double) assetAlertCount / alertCount;

        if (ratio < 0.5) {
            return RcaRuleResult.none(id());
        }

        BigDecimal score = ratio >= 0.8 ? new BigDecimal("0.82") : new BigDecimal("0.65");
        BigDecimal confidence = ratio >= 0.8 ? new BigDecimal("0.70") : new BigDecimal("0.58");

        return new RcaRuleResult(
                id(),
                "Alerts are concentrated on one asset, suggesting a local asset fault",
                score,
                confidence,
                List.of(new RcaEvidence(
                        id(),
                        "Alerts concentrated on same asset",
                        assetAlertCount + " of " + alertCount + " alerts are linked to asset " + top.getKey(),
                        score,
                        confidence,
                        Map.of(
                                "assetId", top.getKey(),
                                "assetAlertCount", assetAlertCount,
                                "totalAlertCount", alertCount,
                                "ratio", ratio
                        )
                ))
        );
    }
}
```

---

## `DependencyRelationRcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/DependencyRelationRcaRule.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class DependencyRelationRcaRule implements RcaRule {
    private static final Set<String> STRONG_RELATIONS = Set.of(
            "depends_on",
            "calls",
            "connects_to",
            "uses"
    );

    @Override
    public String id() {
        return "R5_DEPENDENCY_RELATION";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        var relation = context.assetRelations().stream()
                .filter(item -> item.relationType() != null)
                .filter(item -> STRONG_RELATIONS.contains(item.relationType()))
                .findFirst()
                .orElse(null);

        if (relation == null) {
            return RcaRuleResult.none(id());
        }

        BigDecimal relationConfidence = relation.confidence() == null
                ? new BigDecimal("0.50")
                : relation.confidence();

        BigDecimal score = new BigDecimal("0.68").multiply(relationConfidence).min(new BigDecimal("0.90"));
        BigDecimal confidence = new BigDecimal("0.60").multiply(relationConfidence).min(new BigDecimal("0.85"));

        return new RcaRuleResult(
                id(),
                "Related assets have dependency relationships, suggesting upstream/downstream propagation",
                score,
                confidence,
                List.of(new RcaEvidence(
                        id(),
                        "Dependency relation found",
                        "Asset relation " + relation.relationType()
                                + " exists between " + relation.fromAssetId()
                                + " and " + relation.toAssetId(),
                        score,
                        confidence,
                        Map.of(
                                "relationId", relation.id(),
                                "fromAssetId", relation.fromAssetId(),
                                "toAssetId", relation.toAssetId(),
                                "relationType", relation.relationType(),
                                "relationConfidence", relationConfidence
                        )
                ))
        );
    }
}
```

---

## `TimelineBurstRcaRule.java`

路径：

```txt
modules/aiops-rca/src/main/java/io/aegisops/rca/rules/TimelineBurstRcaRule.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class TimelineBurstRcaRule implements RcaRule {
    @Override
    public String id() {
        return "R6_TIMELINE_BURST";
    }

    @Override
    public RcaRuleResult evaluate(RcaAnalysisContext context) {
        List<OffsetDateTime> times = context.alerts().stream()
                .map(RcaAlertRecord::startsAt)
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();

        if (times.size() < 2) {
            return RcaRuleResult.none(id());
        }

        OffsetDateTime first = times.get(0);
        OffsetDateTime last = times.get(times.size() - 1);
        long minutes = Math.max(0, Duration.between(first, last).toMinutes());

        if (minutes > 10) {
            return RcaRuleResult.none(id());
        }

        BigDecimal score = times.size() >= 5 ? new BigDecimal("0.80") : new BigDecimal("0.60");
        BigDecimal confidence = times.size() >= 5 ? new BigDecimal("0.68") : new BigDecimal("0.52");

        return new RcaRuleResult(
                id(),
                "Alerts occurred in a short burst, suggesting a sudden change or transient infrastructure failure",
                score,
                confidence,
                List.of(new RcaEvidence(
                        id(),
                        "Timeline burst detected",
                        times.size() + " alerts occurred within " + minutes + " minute(s).",
                        score,
                        confidence,
                        Map.of(
                                "alertCount", times.size(),
                                "durationMinutes", minutes,
                                "firstAt", first.toString(),
                                "lastAt", last.toString()
                        )
                ))
        );
    }
}
```

---

# 8. 单元测试

## `RcaTestFixtures.java`

路径：

```txt
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaTestFixtures.java
```

```java
package io.aegisops.rca;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

final class RcaTestFixtures {
    private RcaTestFixtures() {}

    static RcaIncidentRecord incident() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");

        return new RcaIncidentRecord(
                "inc_1",
                "tenant_1",
                "CPU high",
                "summary",
                "critical",
                "open",
                "system",
                "asset_1",
                "zabbix:fp_cpu",
                3,
                null,
                null,
                now.minusMinutes(5),
                now,
                now,
                null,
                now,
                now
        );
    }

    static RcaAlertRecord alert(String id, String severity, String title, String assetId, String fingerprint, int minuteOffset) {
        OffsetDateTime base = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");

        return new RcaAlertRecord(
                id,
                "zabbix",
                "source_" + id,
                severity,
                title,
                "description " + id,
                assetId,
                "host",
                "host-1",
                fingerprint,
                "{}",
                base.plusMinutes(minuteOffset),
                base.plusMinutes(minuteOffset)
        );
    }

    static RcaAnalysisContext contextWithAlerts(List<RcaAlertRecord> alerts) {
        return new RcaAnalysisContext(incident(), alerts, List.of());
    }

    static RcaAssetRelationRecord relation() {
        return new RcaAssetRelationRecord(
                "rel_1",
                "asset_1",
                "asset_2",
                "depends_on",
                new BigDecimal("0.9000"),
                "manual"
        );
    }
}
```

---

## `RcaRulesTest.java`

路径：

```txt
modules/aiops-rca/src/test/java/io/aegisops/rca/rules/RcaRulesTest.java
```

```java
package io.aegisops.rca.rules;

import io.aegisops.rca.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RcaRulesTest {
    @Test
    void highSeverityRuleMatchesCriticalAlert() {
        RcaRule rule = new HighSeverityRcaRule();
        RcaAnalysisContext context = RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0)
        ));

        RcaRuleResult result = rule.evaluate(context);

        assertTrue(result.matched());
        assertEquals("R1_HIGH_SEVERITY", result.ruleId());
        assertEquals(1, result.evidence().size());
    }

    @Test
    void alertVolumeRuleRequiresAtLeastThreeAlerts() {
        RcaRule rule = new AlertVolumeRcaRule();

        RcaRuleResult noMatch = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "A", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "warning", "B", "asset_1", "fp2", 1)
        )));

        assertFalse(noMatch.matched());

        RcaRuleResult matched = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "A", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "warning", "B", "asset_1", "fp2", 1),
                RcaTestFixtures.alert("a3", "warning", "C", "asset_1", "fp3", 2)
        )));

        assertTrue(matched.matched());
    }

    @Test
    void sameFingerprintRuleMatchesRepeatedFingerprint() {
        RcaRule rule = new SameFingerprintRcaRule();

        RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "CPU high", "asset_1", "fp_cpu", 0),
                RcaTestFixtures.alert("a2", "critical", "CPU high", "asset_1", "fp_cpu", 1)
        )));

        assertTrue(result.matched());
        assertEquals("R3_SAME_FINGERPRINT", result.ruleId());
    }

    @Test
    void sameAssetRuleMatchesConcentration() {
        RcaRule rule = new SameAssetConcentrationRcaRule();

        RcaRuleResult result = rule.evaluate(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "CPU high", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "critical", "Memory high", "asset_1", "fp2", 1),
                RcaTestFixtures.alert("a3", "warning", "Disk high", "asset_2", "fp3", 2)
        )));

        assertTrue(result.matched());
        assertEquals("R4_SAME_ASSET_CONCENTRATION", result.ruleId());
    }

    @Test
    void dependencyRuleMatchesStrongRelation() {
        RcaRule rule = new DependencyRelationRcaRule();

        RcaAnalysisContext context = new RcaAnalysisContext(
                RcaTestFixtures.incident(),
                List.of(RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp1", 0)),
                List.of(RcaTestFixtures.relation())
        );

        RcaRuleResult result = rule.evaluate(context);

        assertTrue(result.matched());
        assertEquals("R5_DEPENDENCY_RELATION", result.ruleId());
    }

    @Test
    void timelineBurstRuleMatchesShortWindow() {
        RcaRule rule = new TimelineBurstRcaRule();

        RcaAnalysisContext context = RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "warning", "A", "asset_1", "fp1", 0),
                RcaTestFixtures.alert("a2", "warning", "B", "asset_1", "fp2", 1),
                RcaTestFixtures.alert("a3", "warning", "C", "asset_2", "fp3", 2)
        ));

        RcaRuleResult result = rule.evaluate(context);

        assertTrue(result.matched());
        assertEquals("R6_TIMELINE_BURST", result.ruleId());
    }
}
```

---

## `RcaEngineTest.java`

路径：

```txt
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaEngineTest.java
```

```java
package io.aegisops.rca;

import io.aegisops.rca.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RcaEngineTest {
    @Test
    void analyzeReturnsFallbackWhenNoRuleMatches() {
        RcaEngine engine = new RcaEngine(List.of(
                new HighSeverityRcaRule(),
                new AlertVolumeRcaRule(),
                new SameFingerprintRcaRule(),
                new SameAssetConcentrationRcaRule(),
                new DependencyRelationRcaRule(),
                new TimelineBurstRcaRule()
        ));

        RcaAnalysisResult result = engine.analyze(RcaTestFixtures.contextWithAlerts(List.of(
                RcaTestFixtures.alert("a1", "info", "Info alert", "asset_1", "fp1", 0)
        )));

        assertEquals("No strong root-cause signal found", result.suspectedRootCause());
        assertTrue(result.evidence().isEmpty());
    }

    @Test
    void analyzeCollectsEvidenceAndReturnsTopRootCause() {
        RcaEngine engine = new RcaEngine(List.of(
                new HighSeverityRcaRule(),
                new AlertVolumeRcaRule(),
                new SameFingerprintRcaRule(),
                new SameAssetConcentrationRcaRule(),
                new DependencyRelationRcaRule(),
                new TimelineBurstRcaRule()
        ));

        RcaAnalysisContext context = new RcaAnalysisContext(
                RcaTestFixtures.incident(),
                List.of(
                        RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0),
                        RcaTestFixtures.alert("a2", "warning", "CPU high", "asset_1", "fp_cpu", 1),
                        RcaTestFixtures.alert("a3", "warning", "CPU high", "asset_1", "fp_cpu", 2)
                ),
                List.of(RcaTestFixtures.relation())
        );

        RcaAnalysisResult result = engine.analyze(context);

        assertNotNull(result.suspectedRootCause());
        assertFalse(result.evidence().isEmpty());
        assertTrue(result.confidence().doubleValue() > 0);
        assertTrue(result.summary().contains("RCA matched"));
    }
}
```

---

## `RcaServiceTest.java`

路径：

```txt
modules/aiops-rca/src/test/java/io/aegisops/rca/RcaServiceTest.java
```

```java
package io.aegisops.rca;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.rca.rules.HighSeverityRcaRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RcaServiceTest {
    @Test
    void returnsLatestAnalysisWhenForceDisabled() {
        FakeRcaRepository repository = new FakeRcaRepository();
        repository.incident = RcaTestFixtures.incident();
        repository.latest = new RcaAnalysisRecord(
                "rca_1",
                "tenant_1",
                "inc_1",
                "completed",
                "cached root cause",
                new BigDecimal("0.8000"),
                "cached summary",
                "[]",
                "rules-v1",
                OffsetDateTime.parse("2026-06-14T10:00:00+09:00")
        );

        RcaService service = new RcaService(
                repository,
                new RcaEngine(List.of(new HighSeverityRcaRule())),
                new ObjectMapper()
        );

        RcaAnalysisResponse response = service.analyze("tenant_1", "inc_1", new RcaAnalyzeRequest(false));

        assertEquals("rca_1", response.id());
        assertEquals("cached root cause", response.suspectedRootCause());
        assertEquals(0, repository.savedCount);
    }

    @Test
    void forceAnalyzeCreatesNewAnalysisAndUpdatesIncident() {
        FakeRcaRepository repository = new FakeRcaRepository();
        repository.incident = RcaTestFixtures.incident();
        repository.alerts = List.of(
                RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0)
        );

        RcaService service = new RcaService(
                repository,
                new RcaEngine(List.of(new HighSeverityRcaRule())),
                new ObjectMapper()
        );

        RcaAnalysisResponse response = service.analyze("tenant_1", "inc_1", new RcaAnalyzeRequest(true));

        assertNotNull(response.id());
        assertEquals("inc_1", response.incidentId());
        assertFalse(response.evidence().isEmpty());
        assertEquals(1, repository.savedCount);
        assertEquals(1, repository.timelineCount);
        assertNotNull(repository.updatedRootCause);
        assertTrue(repository.updatedConfidence.doubleValue() > 0);
    }

    @Test
    void latestThrowsWhenNoAnalysisExists() {
        FakeRcaRepository repository = new FakeRcaRepository();
        repository.incident = RcaTestFixtures.incident();

        RcaService service = new RcaService(
                repository,
                new RcaEngine(List.of(new HighSeverityRcaRule())),
                new ObjectMapper()
        );

        AppException ex = assertThrows(AppException.class, () ->
                service.latest("tenant_1", "inc_1")
        );

        assertEquals("RCA_NOT_FOUND", ex.errorCode());
    }

    @Test
    void analyzeThrowsWhenIncidentMissing() {
        FakeRcaRepository repository = new FakeRcaRepository();

        RcaService service = new RcaService(
                repository,
                new RcaEngine(List.of(new HighSeverityRcaRule())),
                new ObjectMapper()
        );

        AppException ex = assertThrows(AppException.class, () ->
                service.analyze("tenant_1", "inc_missing", new RcaAnalyzeRequest(true))
        );

        assertEquals("INCIDENT_NOT_FOUND", ex.errorCode());
    }

    private static final class FakeRcaRepository implements RcaRepository {
        RcaIncidentRecord incident;
        List<RcaAlertRecord> alerts = List.of();
        List<RcaAssetRelationRecord> relations = List.of();
        RcaAnalysisRecord latest;
        RcaAnalysisRecord saved;
        int savedCount;
        int timelineCount;
        String updatedRootCause;
        BigDecimal updatedConfidence;

        @Override
        public Optional<RcaIncidentRecord> findIncident(String tenantId, String incidentId) {
            if (incident == null) {
                return Optional.empty();
            }

            if (!incident.tenantId().equals(tenantId) || !incident.id().equals(incidentId)) {
                return Optional.empty();
            }

            return Optional.of(incident);
        }

        @Override
        public List<RcaAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
            return alerts;
        }

        @Override
        public List<RcaAssetRelationRecord> listAssetRelations(String tenantId, List<String> assetIds) {
            return relations;
        }

        @Override
        public Optional<RcaAnalysisRecord> findLatestAnalysis(String tenantId, String incidentId) {
            return Optional.ofNullable(latest);
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
            savedCount++;
            saved = new RcaAnalysisRecord(
                    id,
                    tenantId,
                    incidentId,
                    "completed",
                    suspectedRootCause,
                    confidence,
                    summary,
                    evidenceJson,
                    modelVersion,
                    OffsetDateTime.parse("2026-06-14T10:00:00+09:00")
            );
        }

        @Override
        public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
            if (saved == null || !saved.tenantId().equals(tenantId) || !saved.id().equals(id)) {
                return Optional.empty();
            }

            return Optional.of(saved);
        }

        @Override
        public void updateIncidentRca(String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
            updatedRootCause = suspectedRootCause;
            updatedConfidence = confidence;
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
            timelineCount++;
        }
    }
}
```

---

# 9. 前端最小接入

## 9.1 替换 `web/console/src/api/client.ts`

```ts
export type ApiResponse<T> = {
  success: boolean;
  data: T;
  errorCode?: string;
  message?: string;
  timestamp: string;
};

export type LoginResponse = {
  token: string;
  user: Me;
};

export type Me = {
  id: string;
  tenantId: string;
  username: string;
  displayName: string;
  roles: string[];
};

export type DataSourceRecord = {
  id: string;
  tenantId: string;
  type: string;
  name: string;
  status: string;
  createdAt: string;
  updatedAt: string;
  lastSyncAt?: string;
};

export type CreateZabbixDataSourcePayload = {
  type: "zabbix";
  name: string;
  zabbix: {
    endpoint: string;
    username?: string;
    password?: string;
    apiToken?: string;
    connectTimeoutSeconds?: number;
    readTimeoutSeconds?: number;
  };
};

export type TestDataSourceResponse = {
  ok: boolean;
  message: string;
  version?: string;
};

export type SyncDataSourceResponse = {
  runId: string;
  status: string;
  hostsCreated: number;
  hostsUpdated: number;
  alertsCreated: number;
  alertsUpdated: number;
  message: string;
};

export type AssetRecord = {
  id: string;
  tenantId: string;
  assetType: string;
  name: string;
  displayName?: string;
  source: string;
  status: string;
  createdAt: string;
};

export type AlertEventRecord = {
  id: string;
  tenantId: string;
  source: string;
  severity: string;
  title: string;
  status: string;
  startsAt: string;
  createdAt: string;
};

export type IncidentRecord = {
  id: string;
  tenantId: string;
  title: string;
  summary?: string;
  severity: string;
  status: string;
  source: string;
  primaryAssetId?: string;
  aggregationKey?: string;
  alertCount: number;
  suspectedRootCause?: string;
  confidence?: number;
  startedAt: string;
  detectedAt: string;
  lastSeenAt?: string;
  resolvedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type IncidentAlertRecord = {
  id: string;
  source: string;
  sourceEventId?: string;
  severity: string;
  title: string;
  status: string;
  assetId?: string;
  entityName?: string;
  fingerprint: string;
  startsAt: string;
  relationType: string;
};

export type IncidentTimelineRecord = {
  id: string;
  eventTime: string;
  eventType: string;
  title: string;
  description?: string;
  source: string;
  payloadJson: string;
};

export type IncidentDetailRecord = {
  incident: IncidentRecord;
  alerts: IncidentAlertRecord[];
  timeline: IncidentTimelineRecord[];
};

export type IncidentAggregationResponse = {
  scannedAlerts: number;
  groups: number;
  incidentsCreated: number;
  incidentsUpdated: number;
  alertsLinked: number;
};

export type RcaEvidence = {
  ruleId: string;
  title: string;
  description: string;
  score: number;
  confidence: number;
  attributes: Record<string, unknown>;
};

export type RcaAnalysisResponse = {
  id: string;
  incidentId: string;
  status: string;
  suspectedRootCause: string;
  confidence: number;
  summary: string;
  evidence: RcaEvidence[];
  modelVersion: string;
  createdAt: string;
};

const TOKEN_KEY = "aegisops_token";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const token = getToken();
  const resp = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {}),
    },
  });

  const payload = await parseApiResponse<T>(resp);
  if (!resp.ok || !payload.success) {
    throw new Error(
      payload.message ||
        payload.errorCode ||
        `Request failed with status ${resp.status}`,
    );
  }
  return payload.data;
}

async function parseApiResponse<T>(resp: Response): Promise<ApiResponse<T>> {
  const contentType = resp.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    const text = await resp.text();
    return {
      success: false,
      data: undefined as T,
      errorCode: `HTTP_${resp.status}`,
      message: text || resp.statusText || "Non-JSON response",
      timestamp: new Date().toISOString(),
    };
  }
  return (await resp.json()) as ApiResponse<T>;
}

export function login(username: string, password: string) {
  return apiRequest<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export function me() {
  return apiRequest<Me>("/api/auth/me");
}

export function overview() {
  return apiRequest<Record<string, number | string>>("/api/system/overview");
}

export function listDataSources() {
  return apiRequest<DataSourceRecord[]>("/api/datasources");
}

export function createZabbixDataSource(payload: CreateZabbixDataSourcePayload) {
  return apiRequest<DataSourceRecord>("/api/datasources", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function testDataSource(id: string) {
  return apiRequest<TestDataSourceResponse>(`/api/datasources/${id}/test`, {
    method: "POST",
  });
}

export function syncDataSource(id: string) {
  return apiRequest<SyncDataSourceResponse>(`/api/datasources/${id}/sync`, {
    method: "POST",
  });
}

export function listAssets() {
  return apiRequest<AssetRecord[]>("/api/assets");
}

export function listAlerts() {
  return apiRequest<AlertEventRecord[]>("/api/alerts");
}

export function listIncidents() {
  return apiRequest<IncidentRecord[]>("/api/incidents");
}

export function aggregateIncidents() {
  return apiRequest<IncidentAggregationResponse>("/api/incidents/aggregate", {
    method: "POST",
    body: JSON.stringify({
      windowMinutes: 1440,
      limit: 1000,
    }),
  });
}

export function getIncident(id: string) {
  return apiRequest<IncidentDetailRecord>(`/api/incidents/${id}`);
}

export function resolveIncident(id: string) {
  return apiRequest<IncidentRecord>(`/api/incidents/${id}/resolve`, {
    method: "POST",
  });
}

export function closeIncident(id: string) {
  return apiRequest<IncidentRecord>(`/api/incidents/${id}/close`, {
    method: "POST",
  });
}

export function analyzeIncidentRca(id: string, force = true) {
  return apiRequest<RcaAnalysisResponse>(`/api/incidents/${id}/rca/analyze`, {
    method: "POST",
    body: JSON.stringify({ force }),
  });
}

export function getLatestIncidentRca(id: string) {
  return apiRequest<RcaAnalysisResponse>(`/api/incidents/${id}/rca/latest`);
}
```

---

## 9.2 `DashboardPage.tsx` 最小改动

不用整页重写。只需要在当前 `DashboardPage.tsx` 中做下面几处修改。

### import 增加

```tsx
import {
  aggregateIncidents,
  analyzeIncidentRca,
  createZabbixDataSource,
  getIncident,
  resolveIncident,
  syncDataSource,
  testDataSource,
} from "../api/client";
```

### state 增加

```tsx
const [rcaResult, setRcaResult] = useState<Awaited<
  ReturnType<typeof analyzeIncidentRca>
> | null>(null);
```

### mutation 增加

```tsx
const rcaMutation = useMutation({
  mutationFn: (incidentId: string) => analyzeIncidentRca(incidentId, true),
  onSuccess: async (result) => {
    setRcaResult(result);
    setMessage(`RCA completed: ${result.suspectedRootCause}`);
    await invalidateAll();
    await queryClient.invalidateQueries({
      queryKey: ["incident", selectedIncidentId],
    });
  },
  onError: (error) => setMessage(String(error)),
});
```

### selected incident 变化时清空 RCA

在组件内加：

```tsx
function selectIncident(id: string) {
  setSelectedIncidentId(id);
  setRcaResult(null);
}
```

然后把 Incident 列表点击从：

```tsx
onClick={() => setSelectedIncidentId(incident.id)}
```

改成：

```tsx
onClick={() => selectIncident(incident.id)}
```

### Incident Detail 按钮区增加 RCA 按钮

在 `Resolve` 按钮旁边加：

```tsx
<button
  className="mt-3 ml-2 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60"
  disabled={rcaMutation.isPending}
  onClick={() => rcaMutation.mutate(incidentDetailQuery.data!.incident.id)}
>
  {rcaMutation.isPending ? "Analyzing..." : "Analyze RCA"}
</button>
```

### Incident Detail 展示 RCA 结果

放在 Linked Alerts 前面：

```tsx
{
  rcaResult && (
    <div>
      <h3 className="text-sm font-semibold">RCA Result</h3>
      <div className="mt-2 rounded-lg border border-indigo-200 bg-indigo-50 p-3 text-sm">
        <div className="font-medium">{rcaResult.suspectedRootCause}</div>
        <div className="mt-1 text-slate-600">
          confidence: {rcaResult.confidence}
        </div>
        <div className="mt-2 text-slate-700">{rcaResult.summary}</div>
      </div>

      <div className="mt-3 space-y-2">
        {rcaResult.evidence.map((item, index) => (
          <div
            className="rounded-lg border border-slate-200 p-3 text-sm"
            key={`${item.ruleId}-${index}`}
          >
            <div className="font-medium">{item.title}</div>
            <div className="text-slate-500">
              {item.ruleId} · score {item.score} · confidence {item.confidence}
            </div>
            <div className="mt-1 text-slate-600">{item.description}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
```

---

# 10. 验证命令

后端单测：

```powershell
mvn -pl modules/aiops-rca -am test
mvn -pl apps/aiops-server -am test
```

前端构建：

```powershell
cd web/console
pnpm build
```

启动：

```powershell
docker compose -f infra/docker-compose.yml up -d
mvn -pl apps/aiops-server -am spring-boot:run
```

---

# 11. Phase3 验收流程

```txt
1. 启动基础设施
2. 启动 aiops-server
3. 登录前端
4. 创建 Zabbix 数据源
5. Sync Zabbix
6. Aggregate Incidents
7. 选择某个 Incident
8. 点击 Analyze RCA
9. 页面展示 suspectedRootCause / confidence / evidence
10. incident 表写入 suspected_root_cause / confidence
11. rca_analysis 表写入一条分析结果
12. incident_timeline 写入 rca_analyzed 事件
```

---

# 12. Phase3 完成后的路线状态

```txt
Phase0：工程地基，完成
Phase1：Zabbix 接入与采集同步，完成
Phase2：Incident 聚合与事故中心，完成
Phase3：规则 RCA 与证据链，完成
Phase4：AI 诊断助手，下一阶段
Phase5：Runbook / Ansible 执行
Phase6：复盘与知识沉淀
```

下一阶段 Phase4 建议做：

```txt
LLM Provider 抽象
RCA evidence -> Prompt
AI Diagnosis Draft
事故解释
排障步骤生成
Runbook 推荐草稿
人工确认后入库
```
