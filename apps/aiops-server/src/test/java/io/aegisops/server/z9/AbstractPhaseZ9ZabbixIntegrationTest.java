package io.aegisops.server.z9;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.AiAgentClient;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.datasource.application.DataSourceSyncApplicationService;
import io.aegisops.incident.IncidentService;
import io.aegisops.integration.zabbix.ZabbixWebhookTokenVerifier;
import io.aegisops.zabbix.ZabbixClient;
import io.aegisops.zabbix.ZabbixClientFactory;
import io.aegisops.zabbix.ZabbixEvent;
import io.aegisops.zabbix.ZabbixHistoryPoint;
import io.aegisops.zabbix.ZabbixHistoryQuery;
import io.aegisops.zabbix.ZabbixItem;
import io.aegisops.zabbix.ZabbixTrendPoint;
import io.aegisops.zabbix.ZabbixTrigger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

abstract class AbstractPhaseZ9ZabbixIntegrationTest {

  protected static final String TENANT_ID = "tenant_z9";
  protected static final String DATASOURCE_ID = "ds_zabbix_z9";
  protected static final String ASSET_ID = "asset_zabbix_z9";
  protected static final String SOURCE_LINK_ID = "asrc_zabbix_z9";
  protected static final String STALE_TENANT_ID = "tenant_z9_stale";
  protected static final String RECOVERED_BEFORE_AGGREGATION_TENANT_ID =
      "tenant_z9_recovered_before_aggregation";
  protected static final String REPLAY_TENANT_ID = "tenant_z9_replay";
  protected static final String POLLING_TENANT_ID = "tenant_z9_polling";
  protected static final String POLLING_DATASOURCE_ID = "ds_zabbix_z9_polling";
  protected static final String WEBHOOK_BEFORE_SYNC_OPEN_TENANT_ID =
      "tenant_z9_webhook_before_sync_open";
  protected static final String WEBHOOK_BEFORE_SYNC_OPEN_DATASOURCE_ID =
      "ds_zabbix_z9_webhook_before_sync_open";
  protected static final String WEBHOOK_BEFORE_SYNC_RESOLVED_TENANT_ID =
      "tenant_z9_webhook_before_sync_resolved";
  protected static final String WEBHOOK_BEFORE_SYNC_RESOLVED_DATASOURCE_ID =
      "ds_zabbix_z9_webhook_before_sync_resolved";

  /**
   * Test-time anchor for all {@code startsAt} / zabbix history clock values. Captured once per
   * {@code @BeforeEach} so the {@code aggregateOpenAlerts(since=now-1440min)} query window picks up
   * the seeded alerts regardless of when the test happens to run.
   */
  protected Instant t0;

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("aiops_z9")
          .withUsername("aiops")
          .withPassword("aiops");

  @DynamicPropertySource
  static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected AlertIngestService alertIngestService;
  @Autowired protected IncidentService incidentService;
  @Autowired protected DataSourceSyncApplicationService syncApplicationService;

  @MockitoBean protected ZabbixWebhookTokenVerifier tokenVerifier;
  @MockitoBean protected ZabbixClientFactory zabbixClientFactory;
  @MockitoBean protected AiAgentClient aiAgentClient;
  @MockitoSpyBean protected OutboxWriter outboxWriter;

  protected ZabbixClient zabbixClient;

  @BeforeEach
  void setUp() {
    t0 = Instant.now();
    TenantContext.setTenantId(TENANT_ID);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "phase-z9-user",
                "n/a",
                List.of(
                    new SimpleGrantedAuthority("incident:read"),
                    new SimpleGrantedAuthority("incident:write"),
                    new SimpleGrantedAuthority("incident:diagnose"),
                    new SimpleGrantedAuthority("datasource:read"),
                    new SimpleGrantedAuthority("evidence:read"),
                    new SimpleGrantedAuthority("evidence:write"),
                    new SimpleGrantedAuthority("rca:read"),
                    new SimpleGrantedAuthority("rca:write"),
                    new SimpleGrantedAuthority("report:read"),
                    new SimpleGrantedAuthority("report:write"))));

    when(tokenVerifier.verify(any(), any())).thenReturn(true);

    zabbixClient = org.mockito.Mockito.mock(ZabbixClient.class);
    when(zabbixClientFactory.create(any())).thenReturn(zabbixClient);

    when(zabbixClient.getItems(any()))
        .thenReturn(
            List.of(
                item("item_cpu", "demo.cpu.util", "AegisOps Demo CPU Utilization", "%"),
                item("item_api", "demo.order.create.time", "AegisOps Demo Order Create Time", "s"),
                item("item_health", "demo.health.status", "AegisOps Demo Health Status", ""),
                item("item_error", "demo.error.count", "AegisOps Demo Error Count", "count")));

    when(zabbixClient.getHistory(any())).thenAnswer(historyAnswer());
    when(zabbixClient.getTrends(any())).thenReturn(List.<ZabbixTrendPoint>of());
    when(zabbixClient.getEvents(any()))
        .thenReturn(
            List.of(
                new ZabbixEvent(
                    "20001",
                    "30001",
                    "CPU High",
                    "4",
                    "1",
                    Instant.parse("2026-06-21T05:10:00Z"),
                    List.of("10084"),
                    Map.of("service", "order-service", "env", "demo"),
                    Map.of())));
    // Anchor a second event fixture to t0 so the aggregate window picks it up regardless of run
    // date.
    when(zabbixClient.getTriggers(any()))
        .thenReturn(
            List.of(
                new ZabbixTrigger(
                    "30001",
                    "CPU High",
                    "last(/aiops-demo-host/demo.cpu.util)>90",
                    "4",
                    "1",
                    List.of("10084"),
                    Map.of("service", "order-service"),
                    Map.of())));

    when(aiAgentClient.diagnose(any())).thenAnswer(inv -> aiResponse(inv.getArgument(0)));

    seedTenantAndDatasource();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  protected void ingestWebhook(String eventId, String triggerId, String title, String severity)
      throws Exception {
    mvc.perform(webhookRequest(eventId, triggerId, title, severity))
        .andExpect(jsonPath("$.data.alertId").exists());
  }

  protected MockHttpServletRequestBuilder webhookRequest(
      String eventId, String triggerId, String title, String severity) throws Exception {
    return webhookRequest(webhookPayload(eventId, triggerId, title, severity));
  }

  protected Map<String, Object> webhookPayload(
      String eventId, String triggerId, String title, String severity) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("datasourceId", DATASOURCE_ID);
    payload.put("eventId", eventId);
    payload.put("problemId", eventId);
    payload.put("triggerId", triggerId);
    payload.put("objectId", triggerId);
    payload.put("status", "PROBLEM");
    payload.put("eventValue", "1");
    payload.put("severity", severity);
    payload.put("title", title);
    payload.put("message", title + " on order-service");
    payload.put("hostId", "10084");
    payload.put("hostName", "aiops-demo-host");
    payload.put("app", "mall");
    payload.put("env", "demo");
    payload.put("service", "order-service");
    payload.put("endpoint", "/api/order/create");
    payload.put("startsAt", t0.minusSeconds(60).toString());
    payload.put("tags", Map.of("service", "order-service", "env", "demo"));

    return payload;
  }

  protected MockHttpServletRequestBuilder webhookRequest(Map<String, Object> payload)
      throws Exception {
    return post("/api/integrations/zabbix/events")
        .param("datasourceId", DATASOURCE_ID)
        .header("X-AegisOps-Webhook-Token", "z9-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(payload));
  }

  private Answer<List<ZabbixHistoryPoint>> historyAnswer() {
    return invocation -> {
      ZabbixHistoryQuery query = invocation.getArgument(0);
      String itemId = query.itemIds().get(0);

      if ("item_cpu".equals(itemId)) {
        return List.of(
            point(itemId, "20", t0.minusSeconds(1200)),
            point(itemId, "95", t0.minusSeconds(60)),
            point(itemId, "96", t0.plusSeconds(600)));
      }

      if ("item_api".equals(itemId)) {
        return List.of(
            point(itemId, "0.12", t0.minusSeconds(1200)),
            point(itemId, "2.5", t0.minusSeconds(60)));
      }

      if ("item_health".equals(itemId)) {
        return List.of(
            point(itemId, "1", t0.minusSeconds(1200)), point(itemId, "0", t0.minusSeconds(60)));
      }

      if ("item_error".equals(itemId)) {
        return List.of(
            point(itemId, "0", t0.minusSeconds(1200)), point(itemId, "12", t0.minusSeconds(60)));
      }

      return List.of();
    };
  }

  private ZabbixItem item(String itemId, String key, String name, String units) {
    return new ZabbixItem(
        itemId,
        "10084",
        name,
        key,
        0,
        units,
        "19",
        "10s",
        Map.of("service", "order-service", "env", "demo"),
        Map.of());
  }

  private ZabbixHistoryPoint point(String itemId, String value, Instant clock) {
    return new ZabbixHistoryPoint(itemId, 0, clock, value, Map.of("value", value));
  }

  protected abstract AgentDiagnosisResponse aiResponse(AgentDiagnosisRequest request);

  private void seedTenantAndDatasource() {
    jdbc.update(
        """
        insert into tenant(id, code, name, status, created_at, updated_at)
        values (?, 'tenant_z9', 'Phase Z9 Tenant', 'active', now(), now())
        on conflict (id) do nothing
        """,
        TENANT_ID);

    jdbc.update(
        """
        insert into datasource(id, tenant_id, name, type, status, config_json, created_at, updated_at)
        values (?, ?, 'Phase Z9 Zabbix', 'zabbix', 'active',
          '{"endpoint": "http://zabbix.local/api_jsonrpc.php", "username": "Admin", "password": "zabbix", "apiToken": null, "connectTimeoutSeconds": 3, "readTimeoutSeconds": 3}'::jsonb,
          now(), now())
        on conflict (id) do update set
          tenant_id = excluded.tenant_id,
          name = excluded.name,
          type = excluded.type,
          status = excluded.status,
          config_json = excluded.config_json,
          updated_at = now()
        """,
        DATASOURCE_ID,
        TENANT_ID);

    jdbc.update(
        """
        insert into asset(id, tenant_id, asset_type, name, display_name, source, source_id,
                          env, status, created_at, updated_at)
        values (?, ?, 'host', 'aiops-demo-host', 'AegisOps Demo Host', 'zabbix', '10084',
                'demo', 'active', now(), now())
        on conflict (id) do update set
          tenant_id = excluded.tenant_id,
          source = excluded.source,
          source_id = excluded.source_id,
          updated_at = now()
        """,
        ASSET_ID,
        TENANT_ID);

    jdbc.update(
        """
        insert into asset_source_link(
          id, tenant_id, asset_id, source_type, source_instance_id, datasource_id,
          external_id, ingestion_channel, sync_status, raw_payload,
          first_seen_at, last_seen_at, created_at, updated_at)
        values (?, ?, ?, 'zabbix', ?, ?, '10084', 'sync', 'active', '{}'::jsonb,
                now(), now(), now(), now())
        on conflict (tenant_id, source_type, source_instance_id, external_id)
        do update set
          asset_id = excluded.asset_id,
          datasource_id = excluded.datasource_id,
          sync_status = 'active',
          updated_at = now()
        """,
        SOURCE_LINK_ID,
        TENANT_ID,
        ASSET_ID,
        DATASOURCE_ID,
        DATASOURCE_ID);
  }

  protected record StatusAt(String status, OffsetDateTime at) {}

  protected record AlertState(
      String status,
      OffsetDateTime endsAt,
      String title,
      String labelsJson,
      String rawPayloadJson,
      String fingerprint,
      String aggregationKey) {}
}
