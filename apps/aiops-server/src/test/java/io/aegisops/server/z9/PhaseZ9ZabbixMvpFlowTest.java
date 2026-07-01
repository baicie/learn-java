package io.aegisops.server.z9;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.AiAgentClient;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.common.tenant.TenantContext;
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
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
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
class PhaseZ9ZabbixMvpFlowTest {

  private static final String TENANT_ID = "tenant_z9";
  private static final String DATASOURCE_ID = "ds_zabbix_z9";

  /**
   * Test-time anchor for all {@code startsAt} / zabbix history clock values. Captured once per
   * {@code @BeforeEach} so the {@code aggregateOpenAlerts(since=now-1440min)} query window picks up
   * the seeded alerts regardless of when the test happens to run.
   */
  private Instant t0;

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

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private ZabbixWebhookTokenVerifier tokenVerifier;
  @MockBean private ZabbixClientFactory zabbixClientFactory;
  @MockBean private AiAgentClient aiAgentClient;

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

    when(tokenVerifier.verify(any())).thenReturn(true);

    ZabbixClient zabbixClient = org.mockito.Mockito.mock(ZabbixClient.class);
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

  @Test
  void shouldRunZabbixMvpFromWebhookToMarkdownReport() throws Exception {
    seedAlerts();
    String incidentId = aggregateAndRequireIncident();
    collectZabbixEvidence(incidentId);
    analyzeRca(incidentId);
    diagnoseWithAi(incidentId);
    generateReportAndAssert(incidentId);
  }

  private void seedAlerts() throws Exception {
    ingestWebhook("20001", "30001", "CPU High", "High");
    ingestWebhook("20002", "30002", "API Slow", "Average");
    ingestWebhook("20003", "30003", "Health Check Failed", "Disaster");
    ingestWebhook("20004", "30004", "Error Log Increased", "Warning");
  }

  private String aggregateAndRequireIncident() throws Exception {
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

  private void collectZabbixEvidence(String incidentId) throws Exception {
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

  private void analyzeRca(String incidentId) throws Exception {
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

  private void diagnoseWithAi(String incidentId) throws Exception {
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

  private void generateReportAndAssert(String incidentId) throws Exception {
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

  private void ingestWebhook(String eventId, String triggerId, String title, String severity)
      throws Exception {
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

    mvc.perform(
            post("/api/integrations/zabbix/events")
                .param("datasourceId", DATASOURCE_ID)
                .header("X-AegisOps-Webhook-Token", "z9-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
        .andExpect(jsonPath("$.data.alertId").exists());
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

  private AgentDiagnosisResponse aiResponse(AgentDiagnosisRequest req) {
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
  }
}
