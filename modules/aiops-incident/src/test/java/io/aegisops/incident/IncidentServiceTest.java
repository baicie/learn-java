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
    assertThat(repository.windowedLookupInvoked).isTrue();
    assertThat(repository.unlinkedLookupInvoked).isFalse();
  }

  @Test
  void shouldUseUnboundedCandidateLookupForWorkerAggregation() {
    FakeIncidentRepository repository =
        new FakeIncidentRepository(
            List.of(alert("a1", "Old Zabbix Alert", "high", "zabbix:ds_1:trigger_old")));
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    IncidentAggregationResponse response = service.aggregateUnlinkedAlerts("tenant_1", 100);

    assertThat(response.alertsScanned()).isOne();
    assertThat(response.alertsLinked()).isOne();
    assertThat(repository.unlinkedLookupInvoked).isTrue();
    assertThat(repository.windowedLookupInvoked).isFalse();
  }

  @Test
  void shouldAutoResolveIncidentWhenAllLinkedAlertsResolved() {
    FakeIncidentRepository repository =
        new FakeIncidentRepository(
            List.of(
                alert("a1", "AegisOps Demo CPU High", "high", "zabbix:ds_1:trigger_cpu", null),
                alert("a2", "AegisOps Demo API Slow", "medium", "zabbix:ds_1:trigger_api", null)));

    repository.linkedAlertCandidates =
        List.of(
            alert("a1", "CPU High", "high", "fp1", OffsetDateTime.parse("2026-06-21T05:20:00Z")),
            alert("a2", "API Slow", "medium", "fp2", OffsetDateTime.parse("2026-06-21T05:21:00Z")));

    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertThat(repository.statusUpdates).contains("inc_1:resolved");
    assertThat(repository.statusUpdateTimes)
        .anyMatch(entry -> entry.startsWith("inc_1:2026-06-21T05:21"));
    assertThat(repository.timelineEvents).contains("incident_resolved");
  }

  @Test
  void shouldNotWriteResolvedTimelineWhenIncidentStatusChangedConcurrently() {
    FakeIncidentRepository repository =
        new FakeIncidentRepository(List.of(alert("a1", "CPU High", "high", "fp1", null)));
    repository.linkedAlertCandidates =
        List.of(
            alert("a1", "CPU High", "high", "fp1", OffsetDateTime.parse("2026-06-21T05:20:00Z")));
    repository.allowAutoResolve = false;
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    service.aggregateUnlinkedAlerts("tenant_1", 100);

    assertThat(repository.statusUpdates).isEmpty();
    assertThat(repository.timelineEvents).doesNotContain("incident_resolved");
  }

  @Test
  void shouldBackfillPrimaryAssetsEvenWhenNoUnlinkedAlertsRemain() {
    FakeIncidentRepository repository = new FakeIncidentRepository(List.of());
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    service.aggregateUnlinkedAlerts("tenant_1", 100);

    assertThat(repository.primaryAssetBackfills).isOne();
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
    private final java.util.Map<String, IncidentSummaryRecord> createdIncidents =
        new java.util.LinkedHashMap<>();
    private final List<String> linkedAlerts = new ArrayList<>();
    private final List<String> statusUpdates = new ArrayList<>();
    private final List<String> statusUpdateTimes = new ArrayList<>();
    private final List<String> timelineEvents = new ArrayList<>();
    private String lastTenantId = "tenant_1";
    private int idCounter = 1;
    private List<AlertCandidate> linkedAlertCandidates = List.of();
    private boolean windowedLookupInvoked;
    private boolean unlinkedLookupInvoked;
    private boolean allowAutoResolve = true;
    private int primaryAssetBackfills;

    FakeIncidentRepository(List<AlertCandidate> candidates) {
      this.candidates = candidates;
    }

    @Override
    public void acquireTenantAggregationLock(String tenantId) {
      lastTenantId = tenantId;
    }

    @Override
    public List<AlertCandidate> findOpenAlertCandidates(
        String tenantId, OffsetDateTime since, int limit) {
      windowedLookupInvoked = true;
      return candidates;
    }

    @Override
    public List<AlertCandidate> findUnlinkedAlertCandidates(String tenantId, int limit) {
      unlinkedLookupInvoked = true;
      return candidates;
    }

    @Override
    public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
      return List.of();
    }

    @Override
    public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
      return createdIncidents.values().stream()
          .filter(inc -> inc.id().equals(incidentId))
          .findFirst();
    }

    @Override
    public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(
        String tenantId, String aggregationKey) {
      return Optional.ofNullable(createdIncidents.get(aggregationKey));
    }

    @Override
    public void insertIncident(IncidentCreateCommand command) {
      String deterministicId = "inc_" + idCounter++;
      insertedIncidents.add(command);
      IncidentSummaryRecord summary =
          new IncidentSummaryRecord(
              deterministicId,
              command.tenantId(),
              command.title(),
              command.summary(),
              command.severity(),
              "open",
              command.source(),
              command.primaryAssetId(),
              command.aggregationKey(),
              0,
              BigDecimal.ZERO,
              command.startedAt(),
              command.startedAt(),
              null,
              null,
              command.startedAt(),
              command.startedAt());
      createdIncidents.put(command.aggregationKey(), summary);
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
      updateStatusAt(tenantId, incidentId, status, terminal, OffsetDateTime.now());
    }

    @Override
    public void updateStatusAt(
        String tenantId,
        String incidentId,
        String status,
        boolean terminal,
        OffsetDateTime resolvedAt) {
      statusUpdates.add(incidentId + ":" + status);
      statusUpdateTimes.add(incidentId + ":" + resolvedAt);
    }

    @Override
    public boolean resolveIfActiveAt(
        String tenantId, String incidentId, OffsetDateTime resolvedAt) {
      if (!allowAutoResolve) {
        return false;
      }
      updateStatusAt(tenantId, incidentId, "resolved", true, resolvedAt);
      return true;
    }

    @Override
    public int backfillPrimaryAssetIds(String tenantId) {
      primaryAssetBackfills++;
      return 0;
    }

    @Override
    public List<IncidentSummaryRecord> findActiveIncidentsReadyToResolve(String tenantId) {
      if (!lastTenantId.equals(tenantId)) {
        return List.of();
      }
      return new ArrayList<>(createdIncidents.values());
    }

    @Override
    public List<AlertCandidate> listLinkedAlertCandidates(String tenantId, String incidentId) {
      return linkedAlertCandidates;
    }
  }
}
