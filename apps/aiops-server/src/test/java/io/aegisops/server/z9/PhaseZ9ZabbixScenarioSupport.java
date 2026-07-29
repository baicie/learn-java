package io.aegisops.server.z9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import io.aegisops.zabbix.ZabbixHost;
import io.aegisops.zabbix.ZabbixProblem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;

abstract class PhaseZ9ZabbixScenarioSupport extends AbstractPhaseZ9ZabbixIntegrationTest {

  protected void seedAlerts() throws Exception {
    ingestWebhook("20001", "30001", "CPU High", "High");
    ingestWebhook("20002", "30002", "API Slow", "Average");
    ingestWebhook("20003", "30003", "Health Check Failed", "Disaster");
    ingestWebhook("20004", "30004", "Error Log Increased", "Warning");
  }

  protected String aggregateAndRequireIncident() throws Exception {
    mvc.perform(
            post("/api/incidents/aggregate")
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "windowMinutes": 1440,
                      "limit": 1000
                    }
                    """))
        .andExpect(jsonPath("$.data.incidentsCreated").value(1));

    String incidentId =
        jdbc.queryForObject(
            """
            select id
            from incident
            where tenant_id = ?
            order by created_at desc
            limit 1
            """,
            String.class,
            TENANT_ID);
    assertThat(incidentId).isNotBlank();
    return incidentId;
  }

  protected void collectZabbixEvidence(String incidentId) throws Exception {
    mvc.perform(
            post("/api/incidents/{incidentId}/evidence/zabbix/collect", incidentId)
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "lookbackMinutes": 30
                    }
                    """))
        .andExpect(jsonPath("$.data.evidenceCreated").isNumber());

    Integer evidenceCount =
        jdbc.queryForObject(
            "select count(*) from diagnosis_evidence where tenant_id = ? and incident_id = ?",
            Integer.class,
            TENANT_ID,
            incidentId);
    assertThat(evidenceCount).isNotNull();
    assertThat(evidenceCount).isGreaterThanOrEqualTo(3);
  }

  protected void analyzeRca(String incidentId) throws Exception {
    mvc.perform(
            post("/api/incidents/{incidentId}/rca/analyze", incidentId)
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "force": true
                    }
                    """))
        .andExpect(jsonPath("$.data.suspectedRootCause").isNotEmpty())
        .andExpect(jsonPath("$.data.matchedRules").isArray())
        .andExpect(jsonPath("$.data.evidenceRefs").isArray());
  }

  protected void diagnoseWithAi(String incidentId) throws Exception {
    mvc.perform(
            post("/api/incidents/{incidentId}/ai/diagnose", incidentId)
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "force": true,
                      "locale": "zh-CN"
                    }
                    """))
        .andExpect(jsonPath("$.data.summary").isNotEmpty())
        .andExpect(jsonPath("$.data.evidenceRefs").isArray())
        .andExpect(jsonPath("$.data.matchedRules").isArray());
  }

  protected void generateReportAndAssert(String incidentId) throws Exception {
    mvc.perform(
            post("/api/incidents/{incidentId}/reports", incidentId)
                .header("X-Tenant-Id", TENANT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "force": true,
                      "locale": "zh-CN",
                      "createdBy": "phase-z9-test"
                    }
                    """))
        .andExpect(jsonPath("$.data.markdownContent").isNotEmpty())
        .andExpect(jsonPath("$.data.versionNo").value(1));

    String markdown =
        jdbc.queryForObject(
            """
            select markdown_content
            from incident_report
            where tenant_id = ? and incident_id = ?
            order by created_at desc
            limit 1
            """,
            String.class,
            TENANT_ID,
            incidentId);

    assertThat(markdown).contains("故障报告");
    assertThat(markdown).contains("关键证据");
    assertThat(markdown).contains("AI 诊断");
  }

  protected void runPollingProblemRecoveryScenario() {
    seedPollingTenantAndDatasource();
    stubPollingHost();

    Instant startedAt = Instant.parse("2026-07-27T05:10:00Z");
    stubPollingProblem(startedAt, objectMapper.createObjectNode());
    syncPollingRun("sync_z9_polling_problem");
    incidentService.aggregateUnlinkedAlerts(POLLING_TENANT_ID, 1000);

    String incidentId = assertOpenPollingAlertAndIncident();
    int outboxCountAfterFirstPoll = incidentAggregationOutboxCount(POLLING_TENANT_ID);
    assertPollingReplayDoesNotEnqueue(outboxCountAfterFirstPoll);

    Instant recoveredAt = Instant.parse("2026-07-27T05:20:00Z");
    syncPollingRecovery(startedAt, recoveredAt, outboxCountAfterFirstPoll);
    assertRecoveredPollingAlertAndIncident(incidentId, recoveredAt);
  }

  protected void seedTenantAndDatasourceWithoutAsset(String tenantId, String datasourceId) {
    jdbc.update(
        """
        insert into tenant(id, code, name, status, created_at, updated_at)
        values (?, ?, 'Phase Z9 Webhook Before Sync Tenant', 'active', now(), now())
        on conflict (id) do nothing
        """,
        tenantId,
        tenantId);
    jdbc.update(
        """
        insert into datasource(id, tenant_id, name, type, status, config_json, created_at, updated_at)
        values (?, ?, 'Phase Z9 Webhook Before Sync Zabbix', 'zabbix', 'active',
          '{"endpoint": "http://zabbix.local/api_jsonrpc.php", "username": "Admin", "password": "zabbix", "apiToken": null, "connectTimeoutSeconds": 3, "readTimeoutSeconds": 3}'::jsonb,
          now(), now())
        on conflict (id) do update set status = 'active', updated_at = now()
        """,
        datasourceId,
        tenantId);
  }

  protected void ingestWebhookBeforeHostSync(
      String datasourceId, String eventId, String statusValue, String eventValue) throws Exception {
    Map<String, Object> payload =
        webhookPayload(eventId, "early-trigger", "Webhook before host sync", "High");
    payload.put("datasourceId", datasourceId);
    payload.put("status", statusValue);
    payload.put("eventValue", eventValue);
    if ("0".equals(eventValue)) {
      payload.put("endsAt", t0.toString());
    }

    mvc.perform(
            post("/api/integrations/zabbix/events")
                .param("datasourceId", datasourceId)
                .header("X-AegisOps-Webhook-Token", "z9-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
        .andExpect(status().isOk());
  }

  protected void runFirstHostSync(String tenantId, String datasourceId, String runId) {
    when(zabbixClient.getHosts(1000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10084",
                    "aiops-demo-host",
                    "AegisOps Demo Host",
                    "0",
                    "10.0.0.84",
                    List.of("AegisOps Demo"),
                    null,
                    objectMapper.createObjectNode())));
    when(zabbixClient.getProblems(1000)).thenReturn(List.of());
    jdbc.update(
        """
        insert into datasource_sync_run(
          id, tenant_id, datasource_id, sync_type, status, started_at, created_by)
        values (?, ?, ?, 'scheduled', 'pending', now(), 'phase-z9-test')
        """,
        runId,
        tenantId,
        datasourceId);

    syncApplicationService.execute(tenantId, datasourceId, runId);
  }

  protected void assertMissingAlertAndIncidentAssets(String tenantId, String eventId) {
    Map<String, Object> alert =
        jdbc.queryForMap(
            "select asset_id from alert_event where tenant_id = ? and source_event_id like ?",
            tenantId,
            "%:" + eventId);
    Map<String, Object> incident =
        jdbc.queryForMap("select primary_asset_id from incident where tenant_id = ?", tenantId);

    assertThat(alert.get("asset_id")).isNull();
    assertThat(incident.get("primary_asset_id")).isNull();
  }

  protected void assertAlertAndIncidentShareCanonicalAsset(String tenantId, String eventId) {
    String alertAssetId =
        jdbc.queryForObject(
            "select asset_id from alert_event where tenant_id = ? and source_event_id like ?",
            String.class,
            tenantId,
            "%:" + eventId);
    String incidentAssetId =
        jdbc.queryForObject(
            "select primary_asset_id from incident where tenant_id = ?", String.class, tenantId);

    assertThat(alertAssetId).isNotBlank();
    assertThat(incidentAssetId).isEqualTo(alertAssetId);
  }

  protected void assertWebhookAlertsLinkedToCanonicalAsset() {
    List<String> assetIds =
        jdbc.queryForList(
            """
            select asset_id
            from alert_event
            where tenant_id = ? and source = 'zabbix'
            order by source_event_id
            """,
            String.class,
            TENANT_ID);

    assertThat(assetIds).containsOnly(ASSET_ID);
  }

  private void seedPollingTenantAndDatasource() {
    jdbc.update(
        """
        insert into tenant(id, code, name, status, created_at, updated_at)
        values (?, ?, 'Phase Z9 Polling Tenant', 'active', now(), now())
        on conflict (id) do nothing
        """,
        POLLING_TENANT_ID,
        POLLING_TENANT_ID);
    jdbc.update(
        """
        insert into datasource(id, tenant_id, name, type, status, config_json, created_at, updated_at)
        values (?, ?, 'Phase Z9 Polling Zabbix', 'zabbix', 'active',
          '{"endpoint": "http://zabbix.local/api_jsonrpc.php", "username": "Admin", "password": "zabbix", "apiToken": null, "connectTimeoutSeconds": 3, "readTimeoutSeconds": 3}'::jsonb,
          now(), now())
        on conflict (id) do update set status = 'active', updated_at = now()
        """,
        POLLING_DATASOURCE_ID,
        POLLING_TENANT_ID);
  }

  private void stubPollingHost() {
    when(zabbixClient.getHosts(1000))
        .thenReturn(
            List.of(
                new ZabbixHost(
                    "10084",
                    "aiops-demo-host",
                    "AegisOps Demo Host",
                    "0",
                    "10.0.0.84",
                    List.of("AegisOps Demo"),
                    null,
                    objectMapper.createObjectNode())));
  }

  private void stubPollingProblem(Instant startedAt, JsonNode rawPayload) {
    when(zabbixClient.getProblems(1000))
        .thenReturn(
            List.of(
                new ZabbixProblem(
                    "20001",
                    "30001",
                    "AegisOps Demo CPU High",
                    4,
                    startedAt,
                    List.of("10084"),
                    Map.of("app", "mall", "env", "demo", "service", "order-service"),
                    rawPayload)));
  }

  private void syncPollingRun(String runId) {
    insertPendingSyncRun(runId);
    syncApplicationService.execute(POLLING_TENANT_ID, POLLING_DATASOURCE_ID, runId);
  }

  private String assertOpenPollingAlertAndIncident() {
    Map<String, Object> openAlert =
        jdbc.queryForMap(
            """
            select status, asset_id, entity_type
            from alert_event
            where tenant_id = ? and source_event_id = ?
            """,
            POLLING_TENANT_ID,
            POLLING_DATASOURCE_ID + ":20001");
    assertThat(openAlert).containsEntry("status", "open").containsEntry("entity_type", "service");
    assertThat(openAlert.get("asset_id")).isNotNull();

    String incidentId =
        jdbc.queryForObject(
            "select id from incident where tenant_id = ?", String.class, POLLING_TENANT_ID);
    assertThat(
            jdbc.queryForObject(
                "select status from incident where tenant_id = ? and id = ?",
                String.class,
                POLLING_TENANT_ID,
                incidentId))
        .isEqualTo("open");
    return incidentId;
  }

  private void assertPollingReplayDoesNotEnqueue(int expectedOutboxCount) {
    syncPollingRun("sync_z9_polling_problem_replay");
    assertThat(incidentAggregationOutboxCount(POLLING_TENANT_ID)).isEqualTo(expectedOutboxCount);
  }

  private void syncPollingRecovery(
      Instant startedAt, Instant recoveredAt, int outboxCountAfterFirstPoll) {
    JsonNode recoveredRaw =
        objectMapper
            .createObjectNode()
            .put("r_eventid", "20002")
            .put("r_clock", recoveredAt.getEpochSecond());
    stubPollingProblem(startedAt, recoveredRaw);

    syncPollingRun("sync_z9_polling_recovery");
    assertThat(incidentAggregationOutboxCount(POLLING_TENANT_ID))
        .isEqualTo(outboxCountAfterFirstPoll + 1);
    incidentService.aggregateUnlinkedAlerts(POLLING_TENANT_ID, 1000);
  }

  private void assertRecoveredPollingAlertAndIncident(String incidentId, Instant recoveredAt) {
    StatusAt recoveredAlert =
        jdbc.queryForObject(
            """
            select status, ends_at
            from alert_event
            where tenant_id = ? and source_event_id = ?
            """,
            (rs, rowNum) ->
                new StatusAt(rs.getString("status"), rs.getObject("ends_at", OffsetDateTime.class)),
            POLLING_TENANT_ID,
            POLLING_DATASOURCE_ID + ":20001");
    assertThat(recoveredAlert.status()).isEqualTo("resolved");
    assertThat(recoveredAlert.at()).isEqualTo(recoveredAt.atOffset(ZoneOffset.UTC));

    StatusAt resolvedIncident =
        jdbc.queryForObject(
            "select status, resolved_at from incident where tenant_id = ? and id = ?",
            (rs, rowNum) ->
                new StatusAt(
                    rs.getString("status"), rs.getObject("resolved_at", OffsetDateTime.class)),
            POLLING_TENANT_ID,
            incidentId);
    assertThat(resolvedIncident.status()).isEqualTo("resolved");
    assertThat(resolvedIncident.at()).isEqualTo(recoveredAt.atOffset(ZoneOffset.UTC));
  }

  private void insertPendingSyncRun(String runId) {
    jdbc.update(
        """
        insert into datasource_sync_run(
          id, tenant_id, datasource_id, sync_type, status, started_at, created_by)
        values (?, ?, ?, 'scheduled', 'pending', now(), 'phase-z9-test')
        """,
        runId,
        POLLING_TENANT_ID,
        POLLING_DATASOURCE_ID);
  }

  private int incidentAggregationOutboxCount(String tenantId) {
    return jdbc.queryForObject(
        """
        select count(*)
        from automation_outbox
        where tenant_id = ? and target_app = 'worker' and job_name = 'incident-aggregate'
        """,
        Integer.class,
        tenantId);
  }
}
