package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import io.aegisops.rca.rules.HighSeverityRcaRule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class RcaServiceTest {
  @Test
  void returnsLatestAnalysisWhenForceDisabled() {
    FakeRcaRepository repository = new FakeRcaRepository();
    repository.incident = RcaTestFixtures.incident();
    repository.latest =
        new RcaAnalysisRecord(
            "rca_1",
            "tenant_1",
            "inc_1",
            "completed",
            "cached root cause",
            new BigDecimal("0.8000"),
            "cached summary",
            "[]",
            "rules-v1",
            OffsetDateTime.parse("2026-06-14T10:00:00+09:00"));

    RcaService service =
        new RcaService(
            repository, new RcaEngine(List.of(new HighSeverityRcaRule())), new ObjectMapper());

    RcaAnalysisResponse response =
        service.analyze("tenant_1", "inc_1", new RcaAnalyzeRequest(false));

    assertEquals("rca_1", response.id());
    assertEquals("cached root cause", response.suspectedRootCause());
    assertEquals(0, repository.savedCount);
  }

  @Test
  void forceAnalyzeCreatesNewAnalysisAndUpdatesIncident() {
    FakeRcaRepository repository = new FakeRcaRepository();
    repository.incident = RcaTestFixtures.incident();
    repository.alerts =
        List.of(RcaTestFixtures.alert("a1", "critical", "CPU high", "asset_1", "fp_cpu", 0));

    RcaService service =
        new RcaService(
            repository, new RcaEngine(List.of(new HighSeverityRcaRule())), new ObjectMapper());

    RcaAnalysisResponse response =
        service.analyze("tenant_1", "inc_1", new RcaAnalyzeRequest(true));

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

    RcaService service =
        new RcaService(
            repository, new RcaEngine(List.of(new HighSeverityRcaRule())), new ObjectMapper());

    AppException ex = assertThrows(AppException.class, () -> service.latest("tenant_1", "inc_1"));

    assertEquals("RCA_NOT_FOUND", ex.errorCode());
  }

  @Test
  void analyzeThrowsWhenIncidentMissing() {
    FakeRcaRepository repository = new FakeRcaRepository();

    RcaService service =
        new RcaService(
            repository, new RcaEngine(List.of(new HighSeverityRcaRule())), new ObjectMapper());

    AppException ex =
        assertThrows(
            AppException.class,
            () -> service.analyze("tenant_1", "inc_missing", new RcaAnalyzeRequest(true)));

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
        String modelVersion) {
      savedCount++;
      saved =
          new RcaAnalysisRecord(
              id,
              tenantId,
              incidentId,
              "completed",
              suspectedRootCause,
              confidence,
              summary,
              evidenceJson,
              modelVersion,
              OffsetDateTime.parse("2026-06-14T10:00:00+09:00"));
    }

    @Override
    public Optional<RcaAnalysisRecord> findAnalysis(String tenantId, String id) {
      if (saved == null || !saved.tenantId().equals(tenantId) || !saved.id().equals(id)) {
        return Optional.empty();
      }

      return Optional.of(saved);
    }

    @Override
    public void updateIncidentRca(
        String tenantId, String incidentId, String suspectedRootCause, BigDecimal confidence) {
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
        String payloadJson) {
      timelineCount++;
    }
  }
}
