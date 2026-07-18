---
title: 多来源资源与证据后续实施计划
type: design
status: accepted
phase: phase-1
owner: ai
created: 2026-07-17
updated: 2026-07-17
related:
  - docs/designs/phase-1/2026-07-16-resource-source-and-entity-resolution-implementation-plan.md
  - docs/phases/phase-1/asset-source-acceptance.md
---

# 多来源资源与证据后续实施计划

> **For agentic workers:** 按本文任务顺序执行，每项先写失败测试，再实现最小代码并运行对应门禁。

**Goal:** 完成 Kubernetes、OpenTelemetry、变更、跨源 RCA、RUM 到 Phase Z9 的多来源 Incident 闭环。

**Architecture:** 外部连接都留在 Adapter；规范实体统一写入 `AssetApplicationService`，告警统一写入 `AlertIngestService`，证据统一由 `aiops-evidence` 查询。Server 只管理连接与接收受控 webhook，长同步由 Worker Outbox 执行。

**Tech Stack:** Java 21、Spring Boot、JDK HttpClient、jOOQ、PostgreSQL、VictoriaMetrics、ClickHouse、React/TypeScript。

**验收结果：** 2026-07-17 已完成全部任务；全仓本地门禁、真实 PostgreSQL/Testcontainers 多来源验收、Portal 测试与构建、Agent 测试均通过。

---

## 范围约束

- Kubernetes 只读 Inventory，不执行命令、不引入 Operator。
- OTLP/RUM 第一版接收标准 JSON/HTTP，指标写 VictoriaMetrics、事件写现有事件存储边界；不自研 Collector/SDK。
- 变更事件复用现有 `change_event`，Service Catalog 只保存 owner/repository/runbook/dependency。
- RCA 使用确定性子分和证据，不把排序交给 LLM，不引入图数据库。
- 所有连接、查询和写入必须带 `tenant_id`；token 不进入响应或审计 payload。

### Task 1: Kubernetes Inventory Adapter

**Files:**

- Create: `modules/aiops-kubernetes-adapter/pom.xml`
- Create: `modules/aiops-kubernetes-adapter/src/main/java/io/aegisops/kubernetes/domain/model/KubernetesResource.java`
- Create: `modules/aiops-kubernetes-adapter/src/main/java/io/aegisops/kubernetes/application/KubernetesInventoryClient.java`
- Create: `modules/aiops-kubernetes-adapter/src/main/java/io/aegisops/kubernetes/infrastructure/adapter/HttpKubernetesInventoryClient.java`
- Create: `modules/aiops-kubernetes-adapter/src/test/java/io/aegisops/kubernetes/infrastructure/adapter/HttpKubernetesInventoryClientTest.java`
- Modify: `pom.xml`, `modules/aiops-datasource`, `apps/aiops-worker`

- [x] 测试 Kubernetes list JSON 被解析为 cluster/node/namespace/workload/pod/service/ingress，owner UID 保留。
- [x] 使用 JDK `HttpClient` 调用只读 list API，Bearer token 只进入 Authorization header。
- [x] Worker 同步逐项调用 `AssetApplicationService.upsert`，Node 携带 `machine_id/provider_id`，资源关系使用 owner/contains。
- [x] 重放同步不产生重复 Asset；缺失 SourceLink 标记为 missing。

### Task 2: OpenTelemetry/APM 接入

**Files:**

- Create: `modules/aiops-otel-adapter/`
- Modify: `modules/aiops-asset`, `modules/aiops-evidence`, `apps/aiops-server`, `apps/aiops-server/src/main/resources/db/migration/`

- [x] 测试 resource attributes 映射 `service.name`、`service.version`、`deployment.environment` 和 `service.instance.id`。
- [x] 接收 OTLP JSON trace/log/metric envelope，限制请求体并校验租户数据源。
- [x] Service/instance 统一写 Asset；trace/log 事件保存可回查 source id；metric 转发 VictoriaMetrics 写入端口。
- [x] Incident evidence 能按 asset/service/time window 返回 trace、log、metric 引用。

### Task 3: 变更事件与轻量 Service Catalog

**Files:**

- Create: `apps/aiops-server/src/main/resources/db/migration/V0040__init_service_catalog_and_ingestion.sql`
- Modify: `modules/aiops-integration`, `modules/aiops-evidence`, `web/portal`

- [x] 测试 GitHub Actions、Deployment、Helm payload 归一成参数化 `change_event`。
- [x] 建立租户隔离的 service catalog：service asset、owner、repository、runbook、dependency。
- [x] 变更进入 Incident 时间窗证据并显示 source/sourceId。

### Task 4: 跨源 RCA 评分

**Files:**

- Modify: `modules/aiops-rca`, `modules/aiops-evidence`, `modules/aiops-incident`

- [x] 定义 entity/time/topology/trigger/change/history 六个 0..1 子分。
- [x] 每个候选至少包含一条可回查证据；无证据候选不进入 Top 3。
- [x] 固定权重并输出总分、子分、解释、sourceId；相同输入排序稳定。

### Task 5: RUM 用户影响

**Files:**

- Create: `modules/aiops-rum-adapter/`
- Modify: `modules/aiops-asset`, `modules/aiops-evidence`, `apps/aiops-server`, `web/portal`

- [x] 校验 page/session/error/web-vitals JSON，拒绝超限或缺少 page/error identity 的事件。
- [x] Page 写 Asset，错误与 trace id 关联，用户标识只保存不可逆 hash。
- [x] Incident evidence 返回受影响 session/page 数与 Web Vitals 聚合，不返回原始用户标识。

### Task 6: 多来源 Phase Z9 验收

**Files:**

- Create: `apps/aiops-server/src/test/java/io/aegisops/server/acceptance/MultiSourceIncidentAcceptanceIT.java`
- Modify: `docs/scenarios/phase-z9-zabbix-mvp-acceptance.md`, `docs/api/`, `docs/INDEX.md`

- [x] 固化 Zabbix/OTel/K8s fixture → Asset/Alert/Incident → Evidence/RCA/AI → Runbook/审批/Runner → Postmortem。
- [x] 验证跨租户不可见、重复摄取幂等、secret 不回显、AI 不直接执行。
- [x] 运行 `bash scripts/ci/verify-local.sh`，全部通过后将本文与验收文档改为 accepted。
