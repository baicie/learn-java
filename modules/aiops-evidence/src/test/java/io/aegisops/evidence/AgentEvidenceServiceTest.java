package io.aegisops.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.evidence.dto.ChangeEvidence;
import io.aegisops.evidence.dto.ChangeEvidenceEvent;
import io.aegisops.evidence.dto.EvidenceQueryRequest;
import io.aegisops.evidence.dto.EvidenceQueryResponse;
import io.aegisops.evidence.dto.LogEvidence;
import io.aegisops.evidence.dto.LogPattern;
import io.aegisops.evidence.dto.MetricEvidence;
import io.aegisops.evidence.dto.MetricSeriesSummary;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEvidenceServiceTest {
  @Test
  void queryNormalizesWindowAndReturnsEvidence() {
    AgentEvidenceProperties properties = new AgentEvidenceProperties("token", 60, 10, 10);

    AgentEvidenceService service =
        new AgentEvidenceService(
            properties,
            request ->
                new MetricEvidence(
                    true,
                    "",
                    List.of(new MetricSeriesSummary("cpu", "query", null, null, null, null, 0))),
            new FakeEvidenceRepository());

    EvidenceQueryResponse response =
        service.query(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                null,
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of("fp_cpu"),
                List.of("CPU high"),
                List.of("checkout-service")));

    assertEquals("tenant_1", response.tenantId());
    assertTrue(response.metrics().available());
    assertTrue(response.logs().available());
    assertTrue(response.changes().available());
  }

  @Test
  void queryKeepsMetricsWhenLogsAndChangesFail() {
    AgentEvidenceProperties properties = new AgentEvidenceProperties("token", 60, 10, 10);

    AgentEvidenceService service =
        new AgentEvidenceService(
            properties,
            request ->
                new MetricEvidence(
                    true,
                    "",
                    List.of(new MetricSeriesSummary("cpu", "query", null, null, null, null, 0))),
            new FailingEvidenceRepository());

    EvidenceQueryResponse response =
        service.query(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of(),
                List.of(),
                List.of()));

    assertTrue(response.metrics().available());
    assertFalse(response.logs().available());
    assertFalse(response.changes().available());
    assertTrue(response.logs().reason().contains("Log evidence query failed"));
    assertTrue(response.changes().reason().contains("Change evidence query failed"));
  }

  @Test
  void queryKeepsLogsAndChangesWhenMetricsFail() {
    AgentEvidenceProperties properties = new AgentEvidenceProperties("token", 60, 10, 10);

    AgentEvidenceService service =
        new AgentEvidenceService(
            properties,
            request -> {
              throw new RuntimeException("victoria down");
            },
            new FakeEvidenceRepository());

    EvidenceQueryResponse response =
        service.query(
            new EvidenceQueryRequest(
                "agent-diagnosis.v1",
                "tenant_1",
                "inc_1",
                "trace_1",
                "asset_1",
                OffsetDateTime.parse("2026-06-16T09:00:00+09:00"),
                OffsetDateTime.parse("2026-06-16T10:00:00+09:00"),
                List.of(),
                List.of(),
                List.of()));

    assertFalse(response.metrics().available());
    assertTrue(response.logs().available());
    assertTrue(response.changes().available());
    assertTrue(response.metrics().reason().contains("Metric evidence query failed"));
  }

  private static final class FakeEvidenceRepository implements EvidenceRepository {
    @Override
    public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
      assertNotNull(request.startedAt());
      assertNotNull(request.lastSeenAt());
      return new LogEvidence(
          true,
          "",
          List.of(new LogPattern("error", "timeout", 3, request.startedAt(), request.lastSeenAt())));
    }

    @Override
    public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
      return new ChangeEvidence(
          true,
          "",
          List.of(
              new ChangeEvidenceEvent(
                  "chg_1",
                  "deploy",
                  "deploy v2",
                  "desc",
                  "jenkins",
                  "alice",
                  "medium",
                  request.lastSeenAt())));
    }
  }

  private static final class FailingEvidenceRepository implements EvidenceRepository {
    @Override
    public LogEvidence queryLogs(EvidenceQueryRequest request, int maxPatterns) {
      throw new RuntimeException("log table unavailable");
    }

    @Override
    public ChangeEvidence queryChanges(EvidenceQueryRequest request, int maxChanges) {
      throw new RuntimeException("change table unavailable");
    }
  }
}
