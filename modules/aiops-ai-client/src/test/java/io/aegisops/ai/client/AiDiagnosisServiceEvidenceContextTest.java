package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import io.aegisops.ai.client.dto.AiAlertRecord;
import io.aegisops.ai.client.dto.AiDiagnoseRequest;
import io.aegisops.ai.client.dto.AiDiagnosisRecord;
import io.aegisops.ai.client.dto.AiDiagnosisResponse;
import io.aegisops.ai.client.dto.AiEvidenceRecord;
import io.aegisops.ai.client.dto.AiIncidentRecord;
import io.aegisops.ai.client.dto.AiRcaRecord;
import io.aegisops.ai.client.dto.AiTimelineRecord;
import io.aegisops.ai.client.dto.SaveDiagnosisCommand;
import io.aegisops.ai.client.dto.TimelineCommand;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiDiagnosisServiceEvidenceContextTest {
  @Test
  void shouldSendEvidenceAndTimelineToAgent() {
    FakeRepository repository = new FakeRepository();
    CapturingAgentClient agentClient = new CapturingAgentClient();

    AiDiagnosisService service =
        new AiDiagnosisService(
            repository,
            agentClient,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            new AgentContractValidator(),
            new AgentObservabilityExtractor(new ObjectMapper()));

    service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));

    AgentDiagnosisRequest request = agentClient.request;

    assertThat(request.evidence()).hasSize(3);
    assertThat(request.evidence().get(0).evidenceType()).isEqualTo("metric_cpu_high");
    assertThat(request.timeline()).hasSize(1);
    assertThat(request.timeline().get(0).eventType()).isEqualTo("alert_linked");
    assertThat(request.rca()).isNotNull();
    assertThat(request.rca().matchedRules()).contains("CPU_API_HEALTH_COMBINED");
    assertThat(request.rca().evidenceRefs()).contains("evd_cpu", "evd_api", "evd_health");
  }

  @Test
  void shouldReturnEvidenceRefsAndMatchedRulesInResponse() {
    FakeRepository repository = new FakeRepository();
    CapturingAgentClient agentClient = new CapturingAgentClient();

    AiDiagnosisService service =
        new AiDiagnosisService(
            repository,
            agentClient,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            new AgentContractValidator(),
            new AgentObservabilityExtractor(new ObjectMapper()));

    AiDiagnosisResponse response =
        service.diagnose("tenant_1", "inc_1", new AiDiagnoseRequest(true, "zh-CN"));

    assertThat(response.matchedRules()).contains("CPU_API_HEALTH_COMBINED");
    assertThat(response.evidenceRefs()).contains("evd_cpu", "evd_api", "evd_health");
    assertThat(response.raw()).isNotNull();
    assertThat(response.raw()).containsKey("matchedRules");
    assertThat(response.raw()).containsKey("evidenceRefs");
  }

  private static final class CapturingAgentClient implements AiAgentClient {
    AgentDiagnosisRequest request;

    @Override
    public AgentDiagnosisResponse diagnose(AgentDiagnosisRequest request) {
      this.request = request;
      return new AgentDiagnosisResponse(
          "agent-diagnosis.v1",
          "inc_1",
          "completed",
          "aiops-agent",
          "langgraph-deterministic",
          "aegisops_diagnosis_graph",
          "order-service 出现 CPU 高位、接口慢和健康检查失败。",
          "疑似 CPU 饱和导致服务响应变慢。",
          "影响 order-service 可用性。",
          List.of("查看 CPU Top 进程"),
          List.of("主机 CPU 高位排查 Runbook"),
          List.of("证据不足时需要补充日志"),
          List.of("CPU_API_HEALTH_COMBINED"),
          List.of("evd_cpu", "evd_api", "evd_health"),
          List.of(),
          Map.of(
              "evidenceRefs", List.of("evd_cpu", "evd_api", "evd_health"),
              "matchedRules", List.of("CPU_API_HEALTH_COMBINED")),
          OffsetDateTime.now());
    }
  }

  private static final class FakeRepository implements AiRepository {
    private SaveDiagnosisCommand savedDiagnosis;
    private final List<TimelineCommand> timelines = new ArrayList<>();

    @Override
    public Optional<AiIncidentRecord> findIncident(String tenantId, String incidentId) {
      return Optional.of(
          new AiIncidentRecord(
              incidentId,
              tenantId,
              "order-service 主机与服务异常",
              "summary",
              "critical",
              "open",
              "zabbix",
              "asset_1",
              "zabbix:ds_1:10084:order-service:demo:202606210510",
              3,
              null,
              null,
              OffsetDateTime.parse("2026-06-21T05:10:00Z"),
              OffsetDateTime.parse("2026-06-21T05:10:00Z"),
              OffsetDateTime.parse("2026-06-21T05:20:00Z"),
              OffsetDateTime.parse("2026-06-21T05:10:00Z"),
              OffsetDateTime.parse("2026-06-21T05:20:00Z")));
    }

    @Override
    public List<AiAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
      return List.of(
          new AiAlertRecord(
              "a1",
              "zabbix",
              "ds_1:20001",
              "high",
              "CPU High",
              "CPU high",
              "asset_1",
              "service",
              "order-service",
              "fp1",
              "{}",
              OffsetDateTime.parse("2026-06-21T05:10:00Z")));
    }

    @Override
    public Optional<AiRcaRecord> findLatestRca(String tenantId, String incidentId) {
      return Optional.of(
          new AiRcaRecord(
              "rca_1",
              "主机 CPU 持续高位导致服务响应变慢，并进一步引发健康检查失败",
              new BigDecimal("0.88"),
              "RCA matched 3 rules",
              """
              [
                {
                  "ruleId": "CPU_API_HEALTH_COMBINED",
                  "attributes": {
                    "evidenceRefs": ["evd_cpu", "evd_api", "evd_health"]
                  }
                }
              ]
              """,
              "rules-v2-evidence",
              OffsetDateTime.parse("2026-06-21T05:22:00Z")));
    }

    @Override
    public List<AiEvidenceRecord> listDiagnosisEvidence(String tenantId, String incidentId) {
      return List.of(
          evidence("evd_cpu", "metric_cpu_high", "CPU 使用率持续高位", "CPU 最大值 96%"),
          evidence("evd_api", "metric_api_slow", "接口响应时间明显升高", "接口最大 2.50s"),
          evidence("evd_health", "metric_health_check_failed", "健康检查失败", "/health 失败"));
    }

    @Override
    public List<AiTimelineRecord> listIncidentTimeline(
        String tenantId, String incidentId, int limit) {
      return List.of(
          new AiTimelineRecord(
              "tl_1",
              OffsetDateTime.parse("2026-06-21T05:10:00Z"),
              "alert_linked",
              "CPU High",
              "CPU alert linked",
              "system",
              "{}"));
    }

    @Override
    public Optional<AiDiagnosisRecord> findLatestDiagnosis(String tenantId, String incidentId) {
      return Optional.empty();
    }

    @Override
    public Optional<AiDiagnosisRecord> findDiagnosis(String tenantId, String diagnosisId) {
      return Optional.of(
          new AiDiagnosisRecord(
              diagnosisId,
              tenantId,
              "inc_1",
              "completed",
              "aiops-agent",
              "langgraph-deterministic",
              "aegisops_diagnosis_graph",
              savedDiagnosis.response().summary(),
              savedDiagnosis.response().rootCause(),
              savedDiagnosis.response().impact(),
              savedDiagnosis.nextStepsJson(),
              savedDiagnosis.runbookSuggestionsJson(),
              savedDiagnosis.risksJson(),
              savedDiagnosis.rawJson(),
              OffsetDateTime.now()));
    }

    @Override
    public void saveDiagnosis(SaveDiagnosisCommand command) {
      this.savedDiagnosis = command;
    }

    @Override
    public void addIncidentTimeline(TimelineCommand command) {
      timelines.add(command);
    }

    private AiEvidenceRecord evidence(String key, String type, String title, String summary) {
      return new AiEvidenceRecord(
          "id_" + key,
          key,
          "zabbix",
          type,
          title,
          summary,
          OffsetDateTime.parse("2026-06-21T05:00:00Z"),
          OffsetDateTime.parse("2026-06-21T05:30:00Z"),
          new BigDecimal("0.86"),
          "{}");
    }
  }
}
