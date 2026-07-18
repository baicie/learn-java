package io.aegisops.server.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.datasource.DataSourceService;
import io.aegisops.datasource.application.KubernetesSyncApplicationService;
import io.aegisops.evidence.AgentEvidenceService;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.integration.api.dto.ChangeIngestRequest;
import io.aegisops.integration.application.OperationsIngestService;
import io.aegisops.kubernetes.application.KubernetesInventoryClient;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.kubernetes.domain.model.KubernetesResource;
import io.aegisops.server.AiOpsServerApplication;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = AiOpsServerApplication.class)
@ActiveProfiles("acceptance")
@Testcontainers(disabledWithoutDocker = true)
class MultiSourceIncidentAcceptanceIT {
  private static final String TENANT_A = "tenant_multi_source_a";
  private static final String TENANT_B = "tenant_multi_source_b";
  private static final String DS_K8S = "ds_k8s_acceptance";
  private static final String DS_OTEL = "ds_otel_acceptance";
  private static final String DS_RUM = "ds_rum_acceptance";
  private static final String DS_CHANGE = "ds_change_acceptance";
  private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-07-17T10:00:00Z");

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("aiops.evidence.victoria.enabled", () -> false);
  }

  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private OperationsIngestService ingestion;
  @Autowired private KubernetesSyncApplicationService kubernetesSync;
  @Autowired private AgentEvidenceService evidence;
  @Autowired private DataSourceService datasources;
  @MockBean private KubernetesInventoryClientFactory kubernetesClients;

  @BeforeEach
  void setUp() {
    jdbc.update("delete from tenant where id in (?, ?)", TENANT_A, TENANT_B);
    insertTenant(TENANT_A, "multi-source-a");
    insertTenant(TENANT_B, "multi-source-b");
    insertDatasource(DS_K8S, TENANT_A, "kubernetes");
    insertDatasource(DS_OTEL, TENANT_A, "opentelemetry");
    insertDatasource(DS_RUM, TENANT_A, "rum");
    insertDatasource(DS_CHANGE, TENANT_A, "github");
  }

  @Test
  void ingestsAndCorrelatesKubernetesOtelRumAndChangeWithinTenant() throws Exception {
    syncKubernetes("sync-k8s-1", true);
    syncKubernetes("sync-k8s-2", false);
    assertThat(count("asset", TENANT_A, "asset_type like 'k8s_%'")).isEqualTo(3);
    assertThat(count("asset_relation", TENANT_A, "source='kubernetes'")).isEqualTo(2);
    assertThat(count("asset_source_link", TENANT_A, "sync_status='missing'")).isEqualTo(1);
    assertThat(datasources.list(TENANT_A).toString()).doesNotContain("secret");

    JsonNode otlp = ingestOtel();
    String serviceAssetId = checkoutServiceAssetId();
    ingestRum();
    ingestChange(serviceAssetId);
    assertStoredEvidence();
    assertCorrelatedEvidence(serviceAssetId);

    assertThat(count("asset", TENANT_B, "true")).isZero();
    assertThatThrownBy(() -> ingestion.ingestOtelBatch(TENANT_B, DS_OTEL, otlp))
        .isInstanceOf(AppException.class);
  }

  private JsonNode ingestOtel() throws Exception {
    JsonNode otlp =
        json.readTree(
            """
            {
              "resourceSpans":[{"resource":{"attributes":[
                {"key":"service.name","value":{"stringValue":"checkout"}},
                {"key":"service.instance.id","value":{"stringValue":"checkout-1"}},
                {"key":"deployment.environment","value":{"stringValue":"prod"}}
              ]},"scopeSpans":[{"spans":[{"traceId":"trace-checkout","spanId":"span-checkout","name":"POST /checkout","startTimeUnixNano":"1784282400000000000"}]}]}],
              "resourceLogs":[{"resource":{"attributes":[
                {"key":"service.name","value":{"stringValue":"checkout"}},
                {"key":"service.instance.id","value":{"stringValue":"checkout-1"}}
              ]},"scopeLogs":[{"logRecords":[{"traceId":"trace-checkout","spanId":"span-checkout","severityText":"ERROR","body":{"stringValue":"payment timeout"},"timeUnixNano":"1784282400000000000"}]}]}],
              "resourceMetrics":[{"resource":{"attributes":[
                {"key":"service.name","value":{"stringValue":"checkout"}},
                {"key":"service.instance.id","value":{"stringValue":"checkout-1"}}
              ]},"scopeMetrics":[{"metrics":[{"name":"http.server.duration","gauge":{"dataPoints":[{"asDouble":12.5,"timeUnixNano":"1784282400000000000"}]}}]}]}]
            }
            """);
    assertThat(ingestion.ingestOtelBatch(TENANT_A, DS_OTEL, otlp).accepted()).isEqualTo(3);
    assertThat(ingestion.ingestOtelBatch(TENANT_A, DS_OTEL, otlp).results())
        .allMatch(result -> !result.created());
    return otlp;
  }

  private String checkoutServiceAssetId() {
    return jdbc.queryForObject(
        "select id from asset where tenant_id=? and asset_type='service' and name='checkout'",
        String.class,
        TENANT_A);
  }

  private void ingestChange(String serviceAssetId) {
    ingestion.ingestChange(
        TENANT_A,
        DS_CHANGE,
        new ChangeIngestRequest(
            "deploy-1",
            "checkout",
            "deployment",
            "Deploy checkout 1.2.0",
            "Helm rollout",
            "github-actions",
            "medium",
            OCCURRED_AT,
            Map.of("chart", "checkout"),
            "payments",
            "https://github.example/checkout",
            null,
            List.of(serviceAssetId)));
  }

  private void assertStoredEvidence() {
    assertThat(count("trace_event", TENANT_A, "true")).isEqualTo(1);
    assertThat(count("log_event", TENANT_A, "source='opentelemetry'")).isEqualTo(1);
    assertThat(count("telemetry_metric", TENANT_A, "true")).isEqualTo(1);
    assertThat(count("rum_event", TENANT_A, "true")).isEqualTo(2);
    assertThat(count("change_event", TENANT_A, "source='github'")).isEqualTo(1);
    assertThat(count("service_catalog", TENANT_A, "true")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select user_hash from rum_event where tenant_id=? and event_type='error'",
                String.class,
                TENANT_A))
        .hasSize(64)
        .doesNotContain("alice");
  }

  private void assertCorrelatedEvidence(String serviceAssetId) {
    var result =
        evidence.query(
            new EvidenceQueryRequest(
                "v1",
                TENANT_A,
                "incident-multi-source",
                "trace-checkout",
                serviceAssetId,
                OCCURRED_AT.minusMinutes(5),
                OCCURRED_AT.plusMinutes(5),
                List.of(),
                List.of(),
                List.of("checkout")));
    assertThat(result.multiSource().available()).isTrue();
    assertThat(result.multiSource().items())
        .extracting(item -> item.type())
        .contains("trace", "metric", "rum");
    assertThat(result.logs().available()).isTrue();
    assertThat(result.changes().available()).isTrue();
    assertThat(result.multiSource().affectedSessions()).isEqualTo(1);
    assertThat(result.multiSource().affectedPages()).isEqualTo(1);
    assertThat(result.multiSource().webVitals()).containsKey("LCP");
  }

  private void syncKubernetes(String runId, boolean includePod) {
    KubernetesInventoryClient client = mock(KubernetesInventoryClient.class);
    when(kubernetesClients.create(any())).thenReturn(client);
    var deployment =
        new KubernetesResource(
            "Deployment",
            "deployment-checkout",
            "checkout",
            "prod",
            Map.of("environment", "prod"),
            List.of(),
            null,
            null,
            null,
            Map.of());
    var pod =
        new KubernetesResource(
            "Pod",
            "pod-checkout-1",
            "checkout-1",
            "prod",
            Map.of(),
            List.of("deployment-checkout"),
            null,
            null,
            "10.0.0.12",
            Map.of());
    when(client.listInventory())
        .thenReturn(includePod ? List.of(deployment, pod) : List.of(deployment));
    jdbc.update(
        "insert into datasource_sync_run(id,tenant_id,datasource_id,status,created_by) values (?,?,?,'pending','acceptance')",
        runId,
        TENANT_A,
        DS_K8S);
    kubernetesSync.execute(TENANT_A, DS_K8S, runId);
  }

  private void ingestRum() throws Exception {
    ingestion.ingestRum(
        TENANT_A,
        DS_RUM,
        json.readTree(
            """
            {"eventId":"rum-error-1","eventType":"error","page":"/checkout","sessionId":"session-1","userId":"alice@example.com","errorMessage":"payment failed","traceId":"trace-checkout","occurredAt":"2026-07-17T10:00:00Z"}
            """));
    ingestion.ingestRum(
        TENANT_A,
        DS_RUM,
        json.readTree(
            """
            {"eventId":"rum-vital-1","eventType":"web_vital","page":"/checkout","sessionId":"session-1","vitalName":"LCP","vitalValue":4.2,"traceId":"trace-checkout","occurredAt":"2026-07-17T10:00:00Z"}
            """));
  }

  private void insertTenant(String id, String code) {
    jdbc.update("insert into tenant(id,code,name,status) values (?,?,?,'active')", id, code, code);
  }

  private void insertDatasource(String id, String tenantId, String type) {
    String config =
        "kubernetes".equals(type)
            ? "{\"endpoint\":\"https://kubernetes.invalid\",\"apiToken\":\"secret\",\"timeoutSeconds\":5}"
            : "{}";
    jdbc.update(
        "insert into datasource(id,tenant_id,type,name,status,config_json) values (?,?,?,?, 'active',?::jsonb)",
        id,
        tenantId,
        type,
        type,
        config);
  }

  private long count(String table, String tenantId, String condition) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where tenant_id=? and " + condition,
        Long.class,
        tenantId);
  }
}
