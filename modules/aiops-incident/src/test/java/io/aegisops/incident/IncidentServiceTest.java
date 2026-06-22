package io.aegisops.incident;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IncidentServiceTest {
  @Test
  void shouldAggregateMultipleZabbixAlertsIntoOneIncidentByAggregationKey() {
    FakeIncidentRepository repository =
        new FakeIncidentRepository(
            List.of(
                alert("a1", "AegisOps Demo CPU High", "high", "zabbix:ds_1:trigger_cpu"),
                alert("a2", "AegisOps Demo API Slow", "medium", "zabbix:ds_1:trigger_api"),
                alert(
                    "a3",
                    "AegisOps Demo Health Check Failed",
                    "critical",
                    "zabbix:ds_1:trigger_health"),
                alert(
                    "a4",
                    "AegisOps Demo Error Log Increased",
                    "medium",
                    "zabbix:ds_1:trigger_log")));

    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    IncidentAggregationResponse response =
        service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertThat(response.alertsScanned()).isEqualTo(4);
    assertThat(response.groups()).isEqualTo(1);
    assertThat(response.incidentsCreated()).isEqualTo(1);
    assertThat(response.alertsLinked()).isEqualTo(4);
    assertThat(repository.insertedIncidents).hasSize(1);
    assertThat(repository.linkedAlerts).hasSize(4);
    assertThat(repository.insertedIncidents.get(0).aggregationKey())
        .isEqualTo("zabbix:ds_1:10084:order-service:demo:202606210510");
  }

  @Test
  void shouldAutoResolveIncidentWhenAllLinkedAlertsResolved() {
    FakeIncidentRepository repository = new FakeIncidentRepository(List.of());
    repository.activeReadyToResolve =
        List.of(
            new IncidentSummaryRecord(
                "inc_1",
                "tenant_1",
                "order-service 主机与服务异常",
                "summary",
                "critical",
                "open",
                "system",
                "asset_1",
                "zabbix:ds_1:10084:order-service:demo:202606210510",
                4,
                BigDecimal.ZERO,
                OffsetDateTime.parse("2026-06-21T05:10:00Z"),
                OffsetDateTime.parse("2026-06-21T05:10:00Z"),
                OffsetDateTime.parse("2026-06-21T05:12:00Z"),
                null,
                OffsetDateTime.parse("2026-06-21T05:10:00Z"),
                OffsetDateTime.parse("2026-06-21T05:12:00Z")));

    repository.linkedAlertCandidates =
        List.of(
            alert("a1", "CPU High", "high", "fp1", OffsetDateTime.parse("2026-06-21T05:20:00Z")),
            alert("a2", "API Slow", "medium", "fp2", OffsetDateTime.parse("2026-06-21T05:21:00Z")));

    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertThat(repository.statusUpdates).contains("inc_1:resolved");
    assertThat(repository.timelineEvents).contains("incident_resolved");
  }

  private static AlertCandidate alert(
      String id, String title, String severity, String fingerprint) {
    return alert(id, title, severity, fingerprint, null);
  }

  private static AlertCandidate alert(
      String id, String title, String severity, String fingerprint, OffsetDateTime endsAt) {
    return new AlertCandidate(
        id,
        "tenant_1",
        "zabbix",
        "ds_1:" + id,
        severity,
        title,
        title,
        "asset_1",
        "service",
        "order-service",
        fingerprint,
        "zabbix:ds_1:10084:order-service:demo:202606210510",
        "{}",
        OffsetDateTime.parse("2026-06-21T05:10:00Z"),
        endsAt,
        OffsetDateTime.parse("2026-06-21T05:10:00Z"));
  }

  private static class FakeIncidentRepository implements IncidentRepository {
    private final List<AlertCandidate> candidates;
    private final List<IncidentCreateCommand> insertedIncidents = new ArrayList<>();
    private final List<String> linkedAlerts = new ArrayList<>();
    private final List<String> statusUpdates = new ArrayList<>();
    private final List<String> timelineEvents = new ArrayList<>();
    private List<IncidentSummaryRecord> activeReadyToResolve = List.of();
    private List<AlertCandidate> linkedAlertCandidates = List.of();

    FakeIncidentRepository(List<AlertCandidate> candidates) {
      this.candidates = candidates;
    }

    @Override
    public void acquireTenantAggregationLock(String tenantId) {}

    @Override
    public List<AlertCandidate> findOpenAlertCandidates(
        String tenantId, OffsetDateTime since, int limit) {
      return candidates;
    }

    @Override
    public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
      return List.of();
    }

    @Override
    public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
      return Optional.empty();
    }

    @Override
    public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(
        String tenantId, String aggregationKey) {
      return Optional.empty();
    }

    @Override
    public void insertIncident(IncidentCreateCommand command) {
      insertedIncidents.add(command);
    }

    @Override
    public void updateIncidentAggregation(IncidentUpdateCommand command) {}

    @Override
    public boolean linkAlert(
        String id,
        String incidentId,
        String alertId,
        String relationType,
        OffsetDateTime occurredAt) {
      linkedAlerts.add(alertId);
      return true;
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timelineEvents.add(command.eventType());
    }

    @Override
    public List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
      return List.of();
    }

    @Override
    public List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId) {
      return List.of();
    }

    @Override
    public int countLinkedAlerts(String incidentId) {
      return linkedAlerts.size();
    }

    @Override
    public void updateStatus(String tenantId, String incidentId, String status, boolean terminal) {
      statusUpdates.add(incidentId + ":" + status);
    }

    @Override
    public List<IncidentSummaryRecord> findActiveIncidentsReadyToResolve(String tenantId) {
      return activeReadyToResolve;
    }

    @Override
    public List<AlertCandidate> listLinkedAlertCandidates(String tenantId, String incidentId) {
      return linkedAlertCandidates;
    }
  }
}
