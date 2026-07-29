package io.aegisops.server.z9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.common.outbox.OutboxMessage;
import io.aegisops.incident.IncidentAggregateRequest;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Phase Z9 end-to-end integration test.
 *
 * <p>Verifies the complete MVP flow: Webhook ingest -> Alert -> Incident aggregation -> Evidence
 * collection -> RCA analysis -> AI diagnosis -> Markdown report.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
class PhaseZ9ZabbixMvpFlowTest extends PhaseZ9ZabbixScenarioSupport {

  @Test
  void shouldRunZabbixMvpFromWebhookToMarkdownReport() throws Exception {
    seedAlerts();
    assertWebhookAlertsLinkedToCanonicalAsset();
    String incidentId = aggregateAndRequireIncident();
    collectZabbixEvidence(incidentId);
    analyzeRca(incidentId);
    diagnoseWithAi(incidentId);
    generateReportAndAssert(incidentId);
  }

  @Test
  void shouldRollbackAlertWhenWorkerDispatchFails() throws Exception {
    doThrow(new IllegalStateException("outbox unavailable"))
        .when(outboxWriter)
        .enqueueOrRequeueFailed(any(OutboxMessage.class));

    mvc.perform(webhookRequest("rollback-event", "rollback-trigger", "CPU High", "High"))
        .andExpect(status().is5xxServerError());

    Integer alertCount =
        jdbc.queryForObject(
            """
            select count(*)
            from alert_event
            where tenant_id = ? and source = 'zabbix' and source_event_id = ?
            """,
            Integer.class,
            TENANT_ID,
            DATASOURCE_ID + ":rollback-event");
    assertThat(alertCount).isZero();
  }

  @Test
  void shouldAggregateOldUnlinkedOpenAlertForWorker() {
    String alertId = "alert_z9_stale";
    jdbc.update(
        """
        insert into tenant(id, code, name, status, created_at, updated_at)
        values (?, ?, 'Phase Z9 Stale Alert Tenant', 'active', now(), now())
        """,
        STALE_TENANT_ID,
        STALE_TENANT_ID);
    jdbc.update(
        """
        insert into alert_event(
          id, tenant_id, source, source_event_id, severity, title, status, fingerprint,
          aggregation_key, starts_at, created_at, updated_at)
        values (?, ?, 'zabbix', 'stale-event', 'high', 'Old unlinked alert', 'open',
                'stale-fingerprint', 'zabbix:stale:host:service:env:bucket',
                now() - interval '30 days', now() - interval '30 days', now() - interval '30 days')
        """,
        alertId,
        STALE_TENANT_ID);

    var response = incidentService.aggregateUnlinkedAlerts(STALE_TENANT_ID, 1000);

    assertThat(response.alertsScanned()).isOne();
    assertThat(response.alertsLinked()).isOne();
    Integer linkCount =
        jdbc.queryForObject(
            """
            select count(*)
            from incident_event
            where event_type = 'alert' and event_id = ?
            """,
            Integer.class,
            alertId);
    assertThat(linkCount).isOne();
  }

  @Test
  void shouldCreateResolvedIncidentWhenAlertRecoversBeforeFirstWorkerAggregation() {
    String alertId = "alert_z9_recovered_before_aggregation";
    OffsetDateTime startedAt = OffsetDateTime.parse("2026-07-27T05:10:00Z");
    OffsetDateTime recoveredAt = OffsetDateTime.parse("2026-07-27T05:11:00Z");
    jdbc.update(
        """
        insert into tenant(id, code, name, status, created_at, updated_at)
        values (?, ?, 'Phase Z9 Recovered Alert Tenant', 'active', now(), now())
        """,
        RECOVERED_BEFORE_AGGREGATION_TENANT_ID,
        RECOVERED_BEFORE_AGGREGATION_TENANT_ID);
    jdbc.update(
        """
        insert into alert_event(
          id, tenant_id, source, source_event_id, severity, title, status, fingerprint,
          aggregation_key, starts_at, ends_at, created_at, updated_at)
        values (?, ?, 'zabbix', 'short-lived-event', 'high', 'Short-lived alert', 'resolved',
                'short-lived-fingerprint', 'zabbix:short:host:service:env:bucket',
                ?, ?, ?, ?)
        """,
        alertId,
        RECOVERED_BEFORE_AGGREGATION_TENANT_ID,
        startedAt,
        recoveredAt,
        startedAt,
        recoveredAt);

    var manualResponse =
        incidentService.aggregateOpenAlerts(
            RECOVERED_BEFORE_AGGREGATION_TENANT_ID, new IncidentAggregateRequest(60, 1000));
    assertThat(manualResponse.alertsScanned()).isZero();

    var workerResponse =
        incidentService.aggregateUnlinkedAlerts(RECOVERED_BEFORE_AGGREGATION_TENANT_ID, 1000);

    assertThat(workerResponse.alertsScanned()).isOne();
    assertThat(workerResponse.incidentsCreated()).isOne();
    assertThat(workerResponse.alertsLinked()).isOne();
    StatusAt incident =
        jdbc.queryForObject(
            "select status, resolved_at from incident where tenant_id = ?",
            (rs, rowNum) ->
                new StatusAt(
                    rs.getString("status"), rs.getObject("resolved_at", OffsetDateTime.class)),
            RECOVERED_BEFORE_AGGREGATION_TENANT_ID);
    assertThat(incident.status()).isEqualTo("resolved");
    assertThat(incident.at()).isEqualTo(recoveredAt);
    Integer linkCount =
        jdbc.queryForObject(
            "select count(*) from incident_event where event_type = 'alert' and event_id = ?",
            Integer.class,
            alertId);
    assertThat(linkCount).isOne();
  }

  @Test
  void shouldNotReopenResolvedAlertWhenOlderOpenEventIsReplayed() {
    OffsetDateTime startedAt = OffsetDateTime.parse("2026-07-27T05:10:00Z");
    OffsetDateTime recoveredAt = OffsetDateTime.parse("2026-07-27T05:20:00Z");
    jdbc.update(
        """
        insert into tenant(id, code, name, status, created_at, updated_at)
        values (?, ?, 'Phase Z9 Replay Tenant', 'active', now(), now())
        """,
        REPLAY_TENANT_ID,
        REPLAY_TENANT_ID);

    alertIngestService.ingest(
        REPLAY_TENANT_ID,
        new AlertIngestRequest(
            "zabbix",
            "replayed-event",
            "high",
            "Recovered before replay",
            null,
            null,
            "host",
            "replay-host",
            Map.of("state", "resolved"),
            startedAt,
            recoveredAt,
            "resolved",
            Map.of("eventid", "replayed-event", "r_eventid", "recovery-event")),
        "replayed-fingerprint",
        "zabbix:replay:host:service:env:bucket");
    alertIngestService.ingest(
        REPLAY_TENANT_ID,
        new AlertIngestRequest(
            "zabbix",
            "replayed-event",
            "high",
            "Older open replay",
            null,
            null,
            "host",
            "replay-host",
            Map.of("state", "open"),
            startedAt,
            null,
            "open",
            Map.of("eventid", "replayed-event")),
        "stale-fingerprint",
        "zabbix:stale:host:service:env:bucket");

    AlertState alert =
        jdbc.queryForObject(
            """
            select status, ends_at, title, labels::text, raw_payload::text,
                   fingerprint, aggregation_key
            from alert_event
            where tenant_id = ? and source = 'zabbix' and source_event_id = 'replayed-event'
            """,
            (rs, rowNum) ->
                new AlertState(
                    rs.getString("status"),
                    rs.getObject("ends_at", OffsetDateTime.class),
                    rs.getString("title"),
                    rs.getString("labels"),
                    rs.getString("raw_payload"),
                    rs.getString("fingerprint"),
                    rs.getString("aggregation_key")),
            REPLAY_TENANT_ID);
    assertThat(alert.status()).isEqualTo("resolved");
    assertThat(alert.endsAt()).isEqualTo(recoveredAt);
    assertThat(alert.title()).isEqualTo("Recovered before replay");
    assertThat(alert.labelsJson()).contains("resolved").doesNotContain("open");
    assertThat(alert.rawPayloadJson()).contains("recovery-event");
    assertThat(alert.fingerprint()).isEqualTo("replayed-fingerprint");
    assertThat(alert.aggregationKey()).isEqualTo("zabbix:replay:host:service:env:bucket");
  }

  @Test
  void shouldNotReopenResolvedWebhookAlertWhenOlderProblemIsReplayed() throws Exception {
    OffsetDateTime startedAt = OffsetDateTime.parse("2026-07-27T05:10:00Z");
    OffsetDateTime recoveredAt = OffsetDateTime.parse("2026-07-27T05:20:00Z");
    Map<String, Object> recovered =
        webhookPayload("webhook-replayed-event", "webhook-trigger", "Recovered webhook", "High");
    recovered.put("status", "OK");
    recovered.put("eventValue", "0");
    recovered.put("startsAt", startedAt.toString());
    recovered.put("endsAt", recoveredAt.toString());
    mvc.perform(webhookRequest(recovered)).andExpect(status().isOk());

    Map<String, Object> olderProblem =
        webhookPayload("webhook-replayed-event", "webhook-trigger", "Older problem", "High");
    olderProblem.put("startsAt", startedAt.toString());
    mvc.perform(webhookRequest(olderProblem)).andExpect(status().isOk());

    StatusAt alert =
        jdbc.queryForObject(
            """
            select status, ends_at
            from alert_event
            where tenant_id = ? and source = 'zabbix' and source_event_id = ?
            """,
            (rs, rowNum) ->
                new StatusAt(rs.getString("status"), rs.getObject("ends_at", OffsetDateTime.class)),
            TENANT_ID,
            DATASOURCE_ID + ":webhook-replayed-event");
    assertThat(alert.status()).isEqualTo("resolved");
    assertThat(alert.at()).isEqualTo(recoveredAt);
  }

  @Test
  void shouldResolvePolledAlertAndIncidentWhenZabbixProblemRecovers() {
    runPollingProblemRecoveryScenario();
  }

  @Test
  void shouldBackfillOpenWebhookAlertAndLinkedIncidentOnFirstHostSync() throws Exception {
    seedTenantAndDatasourceWithoutAsset(
        WEBHOOK_BEFORE_SYNC_OPEN_TENANT_ID, WEBHOOK_BEFORE_SYNC_OPEN_DATASOURCE_ID);
    ingestWebhookBeforeHostSync(
        WEBHOOK_BEFORE_SYNC_OPEN_DATASOURCE_ID, "early-open-event", "PROBLEM", "1");
    incidentService.aggregateUnlinkedAlerts(WEBHOOK_BEFORE_SYNC_OPEN_TENANT_ID, 1000);

    assertMissingAlertAndIncidentAssets(WEBHOOK_BEFORE_SYNC_OPEN_TENANT_ID, "early-open-event");

    runFirstHostSync(
        WEBHOOK_BEFORE_SYNC_OPEN_TENANT_ID,
        WEBHOOK_BEFORE_SYNC_OPEN_DATASOURCE_ID,
        "sync_z9_webhook_before_sync_open");

    assertAlertAndIncidentShareCanonicalAsset(
        WEBHOOK_BEFORE_SYNC_OPEN_TENANT_ID, "early-open-event");
  }

  @Test
  void shouldBackfillResolvedWebhookAlertAndLinkedIncidentOnFirstHostSync() throws Exception {
    seedTenantAndDatasourceWithoutAsset(
        WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID, WEBHOOK_BEFORE_SYNC_RESOLVED_DATASOURCE_ID);
    ingestWebhookBeforeHostSync(
        WEBHOOK_BEFORE_SYNC_RESOLVED_DATASOURCE_ID, "early-resolved-event", "PROBLEM", "1");
    incidentService.aggregateUnlinkedAlerts(WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID, 1000);

    ingestWebhookBeforeHostSync(
        WEBHOOK_BEFORE_SYNC_RESOLVED_DATASOURCE_ID, "early-resolved-event", "OK", "0");
    incidentService.aggregateUnlinkedAlerts(WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID, 1000);

    assertThat(
            jdbc.queryForObject(
                "select status from alert_event where tenant_id = ? and source_event_id = ?",
                String.class,
                WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID,
                WEBHOOK_BEFORE_SYNC_RESOLVED_DATASOURCE_ID + ":early-resolved-event"))
        .isEqualTo("resolved");
    assertThat(
            jdbc.queryForObject(
                "select status from incident where tenant_id = ?",
                String.class,
                WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID))
        .isEqualTo("resolved");
    assertMissingAlertAndIncidentAssets(
        WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID, "early-resolved-event");

    runFirstHostSync(
        WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID,
        WEBHOOK_BEFORE_SYNC_RESOLVED_DATASOURCE_ID,
        "sync_z9_webhook_before_sync_resolved");

    assertAlertAndIncidentShareCanonicalAsset(
        WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID, "early-resolved-event");
  }

  @Override
  protected AgentDiagnosisResponse aiResponse(AgentDiagnosisRequest req) {
    List<String> ns = List.of("查看 CPU Top 进程", "检查发布记录", "必要时扩容");
    List<String> rb = List.of("CPU 巡检 Runbook");
    List<String> rs = List.of("需人工确认扩容方案");
    List<String> mr = List.of("CPU_API_HEALTH_COMBINED");
    List<String> er = List.of("evd_cpu", "evd_api", "evd_health");
    List<Map<String, Object>> tl = List.of();
    Map<String, Object> rw = new HashMap<>();
    rw.put("generationMode", "phase-z9-mock");

    return new AgentDiagnosisResponse(
        req.contractVersion(),
        req.incidentId(),
        "completed",
        "aiops-agent",
        "langgraph-deterministic",
        "aegisops_diagnosis_graph",
        "order-service 在故障窗口内同时出现 CPU 持续高位、接口响应变慢和健康检查失败。",
        "疑似主机 CPU 饱和导致服务响应变慢，并进一步引发健康检查失败。",
        "影响 order-service 的请求可用性。",
        ns,
        rb,
        rs,
        mr,
        er,
        tl,
        rw,
        OffsetDateTime.now());
  }
}
