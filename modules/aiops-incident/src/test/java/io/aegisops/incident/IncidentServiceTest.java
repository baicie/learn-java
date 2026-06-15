package io.aegisops.incident;

import static org.junit.jupiter.api.Assertions.*;

import io.aegisops.common.exception.AppException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class IncidentServiceTest {
  @Test
  void aggregatesAlertsWithSameFingerprintIntoOneIncident() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    repository.candidates.add(alert("alert_1", "critical", "CPU high", "fp_cpu"));
    repository.candidates.add(alert("alert_2", "warning", "CPU high", "fp_cpu"));

    IncidentAggregationResponse response =
        service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertEquals(2, response.scannedAlerts());
    assertEquals(1, response.groups());
    assertEquals(1, response.incidentsCreated());
    assertEquals(0, response.incidentsUpdated());
    assertEquals(2, response.alertsLinked());
    assertEquals(1, repository.tenantAggregationLockCount);

    assertEquals(1, repository.incidents.size());

    IncidentSummaryRecord incident = repository.incidents.values().iterator().next();
    assertEquals("critical", incident.severity());
    assertEquals(2, incident.alertCount());
    assertEquals("open", incident.status());
    assertEquals(2, repository.linkedAlerts.size());
    assertEquals(2, repository.timeline.size());
  }

  @Test
  void aggregatesDifferentFingerprintsIntoDifferentIncidents() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    repository.candidates.add(alert("alert_1", "critical", "CPU high", "fp_cpu"));
    repository.candidates.add(alert("alert_2", "warning", "Disk high", "fp_disk"));

    IncidentAggregationResponse response =
        service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertEquals(2, response.groups());
    assertEquals(2, response.incidentsCreated());
    assertEquals(2, repository.incidents.size());
  }

  @Test
  void reusesActiveIncidentByAggregationKey() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    AlertCandidate existingAlert = alert("alert_old", "warning", "CPU high", "fp_cpu");
    String aggregationKey = new IncidentAggregationPolicy().aggregationKey(existingAlert);

    IncidentSummaryRecord existing =
        summary("inc_existing", "tenant_1", "CPU high", "warning", "open", aggregationKey, 1);

    repository.incidents.put(existing.id(), existing);
    repository.activeByAggregationKey.put(aggregationKey, existing);
    repository.linkedAlertIds.add("alert_old");
    repository.candidates.add(alert("alert_new", "critical", "CPU high", "fp_cpu"));

    IncidentAggregationResponse response =
        service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertEquals(0, response.incidentsCreated());
    assertEquals(1, response.incidentsUpdated());
    assertEquals(1, repository.incidents.size());

    IncidentSummaryRecord updated = repository.incidents.get("inc_existing");
    assertEquals("critical", updated.severity());
    assertEquals(2, updated.alertCount());
  }

  @Test
  void doesNotOverCountWhenLinkAlreadyExists() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    AlertCandidate alert = alert("alert_1", "critical", "CPU high", "fp_cpu");
    String aggregationKey = new IncidentAggregationPolicy().aggregationKey(alert);

    IncidentSummaryRecord existing =
        summary("inc_existing", "tenant_1", "CPU high", "warning", "open", aggregationKey, 1);

    repository.incidents.put(existing.id(), existing);
    repository.activeByAggregationKey.put(aggregationKey, existing);
    repository.linkedAlertIds.add("alert_1");
    repository.candidates.add(alert);

    IncidentAggregationResponse response =
        service.aggregateOpenAlerts("tenant_1", new IncidentAggregateRequest(60, 100));

    assertEquals(1, response.scannedAlerts());
    assertEquals(1, response.groups());
    assertEquals(0, response.incidentsCreated());
    assertEquals(0, response.incidentsUpdated());
    assertEquals(0, response.alertsLinked());
    assertEquals(1, repository.incidents.get("inc_existing").alertCount());
    assertTrue(repository.timeline.isEmpty());
  }

  @Test
  void updateStatusWritesTimeline() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    IncidentSummaryRecord incident =
        summary("inc_1", "tenant_1", "CPU high", "critical", "open", "zabbix:fp_cpu", 2);
    repository.incidents.put(incident.id(), incident);
    repository.activeByAggregationKey.put(incident.aggregationKey(), incident);

    IncidentRecord updated =
        service.updateStatus("tenant_1", "inc_1", new IncidentStatusRequest("resolved", "fixed"));

    assertEquals("resolved", updated.status());
    assertNotNull(updated.resolvedAt());
    assertEquals(1, repository.timeline.size());
    assertEquals("status_changed", repository.timeline.get(0).eventType());
    assertFalse(repository.activeByAggregationKey.containsKey("zabbix:fp_cpu"));
  }

  @Test
  void updateStatusRejectsInvalidStatus() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    repository.incidents.put(
        "inc_1", summary("inc_1", "tenant_1", "CPU high", "critical", "open", "zabbix:fp_cpu", 1));

    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                service.updateStatus("tenant_1", "inc_1", new IncidentStatusRequest("bad", null)));

    assertEquals("INCIDENT_STATUS_INVALID", ex.errorCode());
  }

  @Test
  void reopeningTerminalIncidentFailsWhenAnotherActiveIncidentExistsForSameAggregationKey() {
    FakeIncidentRepository repository = new FakeIncidentRepository();
    IncidentService service = new IncidentService(repository, new IncidentAggregationPolicy());

    IncidentSummaryRecord closed =
        summary("inc_closed", "tenant_1", "CPU high", "critical", "closed", "zabbix:fp_cpu", 2);

    IncidentSummaryRecord active =
        summary("inc_active", "tenant_1", "CPU high again", "warning", "open", "zabbix:fp_cpu", 1);

    repository.incidents.put(closed.id(), closed);
    repository.incidents.put(active.id(), active);
    repository.activeByAggregationKey.put(active.aggregationKey(), active);

    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                service.updateStatus(
                    "tenant_1", "inc_closed", new IncidentStatusRequest("open", "reopen")));

    assertEquals("INCIDENT_ACTIVE_CONFLICT", ex.errorCode());
  }

  private AlertCandidate alert(String id, String severity, String title, String fingerprint) {
    return new AlertCandidate(
        id,
        "tenant_1",
        "zabbix",
        "source_" + id,
        severity,
        title,
        "desc " + id,
        "asset_1",
        "host",
        "host-1",
        fingerprint,
        OffsetDateTime.parse("2026-06-14T10:00:00+09:00"),
        OffsetDateTime.parse("2026-06-14T10:00:00+09:00"));
  }

  private IncidentSummaryRecord summary(
      String id,
      String tenantId,
      String title,
      String severity,
      String status,
      String aggregationKey,
      int alertCount) {
    OffsetDateTime now = OffsetDateTime.parse("2026-06-14T10:00:00+09:00");
    OffsetDateTime resolvedAt =
        List.of("resolved", "closed", "ignored").contains(status) ? now : null;

    return new IncidentSummaryRecord(
        id,
        tenantId,
        title,
        "summary",
        severity,
        status,
        "system",
        "asset_1",
        aggregationKey,
        alertCount,
        BigDecimal.ZERO,
        now,
        now,
        now,
        resolvedAt,
        now,
        now);
  }

  private static final class FakeIncidentRepository implements IncidentRepository {
    final List<AlertCandidate> candidates = new ArrayList<>();
    final Map<String, IncidentSummaryRecord> incidents = new LinkedHashMap<>();
    final Map<String, IncidentSummaryRecord> activeByAggregationKey = new LinkedHashMap<>();
    final List<IncidentAlertRecord> linkedAlerts = new ArrayList<>();
    final Set<String> linkedAlertIds = new HashSet<>();
    final List<IncidentTimelineRecord> timeline = new ArrayList<>();
    int tenantAggregationLockCount = 0;

    @Override
    public void acquireTenantAggregationLock(String tenantId) {
      tenantAggregationLockCount++;
    }

    @Override
    public List<AlertCandidate> findOpenAlertCandidates(
        String tenantId, OffsetDateTime since, int limit) {
      return candidates.stream()
          .filter(alert -> alert.tenantId().equals(tenantId))
          .limit(limit)
          .toList();
    }

    @Override
    public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
      return incidents.values().stream()
          .filter(incident -> incident.tenantId().equals(tenantId))
          .limit(limit)
          .toList();
    }

    @Override
    public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
      IncidentSummaryRecord incident = incidents.get(incidentId);
      if (incident == null || !incident.tenantId().equals(tenantId)) {
        return Optional.empty();
      }

      return Optional.of(incident);
    }

    @Override
    public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(
        String tenantId, String aggregationKey) {
      IncidentSummaryRecord incident = activeByAggregationKey.get(aggregationKey);
      if (incident == null || !incident.tenantId().equals(tenantId)) {
        return Optional.empty();
      }

      return Optional.of(incident);
    }

    @Override
    public void insertIncident(IncidentCreateCommand command) {
      IncidentSummaryRecord record =
          new IncidentSummaryRecord(
              command.id(),
              command.tenantId(),
              command.title(),
              command.summary(),
              command.severity(),
              "open",
              command.source(),
              command.primaryAssetId(),
              command.aggregationKey(),
              command.alertCount(),
              BigDecimal.ZERO,
              command.startedAt(),
              command.detectedAt(),
              command.lastSeenAt(),
              null,
              command.detectedAt(),
              command.detectedAt());

      incidents.put(record.id(), record);
      activeByAggregationKey.put(record.aggregationKey(), record);
    }

    @Override
    public void updateIncidentAggregation(
        String tenantId,
        String incidentId,
        String title,
        String summary,
        String severity,
        int alertCount,
        OffsetDateTime lastSeenAt) {
      IncidentSummaryRecord old = incidents.get(incidentId);
      IncidentSummaryRecord updated =
          new IncidentSummaryRecord(
              old.id(),
              old.tenantId(),
              title,
              summary,
              severity,
              old.status(),
              old.source(),
              old.primaryAssetId(),
              old.aggregationKey(),
              alertCount,
              old.impactScore(),
              old.startedAt(),
              old.detectedAt(),
              lastSeenAt,
              old.resolvedAt(),
              old.createdAt(),
              OffsetDateTime.now());

      incidents.put(incidentId, updated);
      if (List.of("open", "investigating", "mitigating").contains(updated.status())) {
        activeByAggregationKey.put(updated.aggregationKey(), updated);
      }
    }

    @Override
    public boolean linkAlert(
        String id,
        String incidentId,
        String alertId,
        String relationType,
        OffsetDateTime occurredAt) {
      if (!linkedAlertIds.add(alertId)) {
        return false;
      }

      linkedAlerts.add(
          new IncidentAlertRecord(
              alertId,
              "zabbix",
              "source_" + alertId,
              "warning",
              "alert " + alertId,
              "open",
              "asset_1",
              "host-1",
              "fp",
              occurredAt,
              relationType));

      return true;
    }

    @Override
    public void addTimeline(TimelineCreateCommand command) {
      timeline.add(
          new IncidentTimelineRecord(
              command.id(),
              command.eventTime(),
              command.eventType(),
              command.title(),
              command.description(),
              command.source(),
              command.payloadJson()));
    }

    @Override
    public List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
      return linkedAlerts;
    }

    @Override
    public List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId) {
      return timeline;
    }

    @Override
    public int countLinkedAlerts(String incidentId) {
      return linkedAlertIds.size();
    }

    @Override
    public void updateStatus(String tenantId, String incidentId, String status, boolean terminal) {
      IncidentSummaryRecord old = incidents.get(incidentId);
      IncidentSummaryRecord updated =
          new IncidentSummaryRecord(
              old.id(),
              old.tenantId(),
              old.title(),
              old.summary(),
              old.severity(),
              status,
              old.source(),
              old.primaryAssetId(),
              old.aggregationKey(),
              old.alertCount(),
              old.impactScore(),
              old.startedAt(),
              old.detectedAt(),
              old.lastSeenAt(),
              terminal ? OffsetDateTime.now() : null,
              old.createdAt(),
              OffsetDateTime.now());

      incidents.put(incidentId, updated);

      if (List.of("open", "investigating", "mitigating").contains(status)) {
        activeByAggregationKey.put(updated.aggregationKey(), updated);
      } else {
        activeByAggregationKey.remove(updated.aggregationKey());
      }
    }
  }
}
