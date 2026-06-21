package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {
  private static final List<String> ACTIVE_STATUSES =
      List.of("open", "investigating", "mitigating");

  private static final List<String> TERMINAL_STATUSES = List.of("resolved", "closed", "ignored");

  private static final List<String> ALLOWED_STATUSES =
      List.of("open", "investigating", "mitigating", "resolved", "closed", "ignored");

  private final IncidentRepository repository;
  private final IncidentAggregationPolicy policy;

  public IncidentService(IncidentRepository repository, IncidentAggregationPolicy policy) {
    this.repository = repository;
    this.policy = policy;
  }

  public List<IncidentRecord> list(String tenantId) {
    return repository.listIncidents(tenantId, 100).stream().map(IncidentRecord::from).toList();
  }

  public IncidentDetailRecord detail(String tenantId, String incidentId) {
    IncidentRecord incident =
        repository
            .findIncident(tenantId, incidentId)
            .map(IncidentRecord::from)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    return new IncidentDetailRecord(
        incident,
        repository.listIncidentAlerts(tenantId, incidentId),
        repository.listTimeline(tenantId, incidentId));
  }

  public List<IncidentAlertRecord> alerts(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);
    return repository.listIncidentAlerts(tenantId, incidentId);
  }

  public List<IncidentTimelineRecord> timeline(String tenantId, String incidentId) {
    ensureIncidentExists(tenantId, incidentId);
    return repository.listTimeline(tenantId, incidentId);
  }

  @Transactional
  public IncidentAggregationResponse aggregateOpenAlerts(
      String tenantId, IncidentAggregateRequest request) {
    repository.acquireTenantAggregationLock(tenantId);

    IncidentAggregateRequest normalizedRequest =
        request == null ? new IncidentAggregateRequest(null, null) : request;

    OffsetDateTime since =
        OffsetDateTime.now().minusMinutes(normalizedRequest.normalizedWindowMinutes());

    List<AlertCandidate> candidates =
        repository.findOpenAlertCandidates(tenantId, since, normalizedRequest.normalizedLimit());

    Map<String, List<AlertCandidate>> groups =
        candidates.stream()
            .collect(
                Collectors.groupingBy(
                    policy::aggregationKey, LinkedHashMap::new, Collectors.toList()));

    int incidentsCreated = 0;
    int incidentsUpdated = 0;
    int alertsLinked = 0;

    for (Map.Entry<String, List<AlertCandidate>> entry : groups.entrySet()) {
      String aggregationKey = entry.getKey();
      List<AlertCandidate> alerts = entry.getValue();

      var result = processGroup(tenantId, aggregationKey, alerts);
      if (result.created()) {
        incidentsCreated++;
      } else if (result.linked() > 0) {
        incidentsUpdated++;
      }
      alertsLinked += result.linked();
    }

    return new IncidentAggregationResponse(
        candidates.size(), groups.size(), incidentsCreated, incidentsUpdated, alertsLinked);
  }

  private record GroupResult(boolean created, int linked) {}

  private GroupResult processGroup(
      String tenantId, String aggregationKey, List<AlertCandidate> alerts) {
    if (alerts.isEmpty()) {
      return new GroupResult(false, 0);
    }

    var existingIncident = repository.findActiveIncidentByAggregationKey(tenantId, aggregationKey);
    String groupSeverity = policy.highestSeverity(alerts);
    String incidentId;
    boolean created = false;

    if (existingIncident.isPresent()) {
      incidentId = existingIncident.get().id();
    } else {
      incidentId = newId("inc");
      created = true;

      repository.insertIncident(
          new IncidentCreateCommand(
              incidentId,
              tenantId,
              policy.title(aggregationKey, alerts),
              policy.summary(aggregationKey, alerts),
              groupSeverity,
              "system",
              policy.primaryAssetId(alerts),
              aggregationKey,
              0,
              policy.firstStartedAt(alerts),
              OffsetDateTime.now(),
              policy.lastSeenAt(alerts)));
    }

    int linkedInGroup = 0;
    int index = 0;

    for (AlertCandidate alert : alerts) {
      boolean linked =
          repository.linkAlert(
              newId("ie"),
              incidentId,
              alert.id(),
              index == 0 ? "primary" : "related",
              alert.startsAt());

      if (!linked) {
        index++;
        continue;
      }

      repository.addTimeline(
          new TimelineCreateCommand(
              newId("tl"),
              incidentId,
              alert.startsAt(),
              "alert_linked",
              alert.title(),
              alert.description(),
              "system",
              alertPayload(alert, aggregationKey)));

      linkedInGroup++;
      index++;
    }

    if (linkedInGroup == 0) {
      return new GroupResult(created, 0);
    }

    int actualAlertCount = repository.countLinkedAlerts(incidentId);
    String mergedSeverity =
        existingIncident
            .map(existing -> IncidentSeverity.max(existing.severity(), groupSeverity))
            .orElse(groupSeverity);

    repository.updateIncidentAggregation(
        new IncidentUpdateCommand(
            tenantId,
            incidentId,
            policy.title(aggregationKey, alerts),
            policy.summary(aggregationKey, alerts),
            mergedSeverity,
            actualAlertCount,
            policy.lastSeenAt(alerts)));

    return new GroupResult(created, linkedInGroup);
  }

  @Transactional
  public IncidentRecord updateStatus(
      String tenantId, String incidentId, IncidentStatusRequest request) {
    IncidentSummaryRecord current =
        repository
            .findIncident(tenantId, incidentId)
            .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

    String targetStatus = normalizeStatus(request == null ? null : request.status());
    validateStatusTransition(tenantId, current, targetStatus);

    boolean terminal = TERMINAL_STATUSES.contains(targetStatus);

    repository.updateStatus(tenantId, incidentId, targetStatus, terminal);
    repository.addTimeline(
        new TimelineCreateCommand(
            newId("tl"),
            incidentId,
            OffsetDateTime.now(),
            "status_changed",
            "Incident status changed to " + targetStatus,
            request == null ? null : request.note(),
            "user",
            "{\"status\":\"" + targetStatus + "\"}"));

    return repository
        .findIncident(tenantId, incidentId)
        .map(IncidentRecord::from)
        .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));
  }

  public IncidentRecord resolve(String tenantId, String incidentId) {
    return updateStatus(
        tenantId, incidentId, new IncidentStatusRequest("resolved", "Resolved by user"));
  }

  public IncidentRecord close(String tenantId, String incidentId) {
    return updateStatus(
        tenantId, incidentId, new IncidentStatusRequest("closed", "Closed by user"));
  }

  private void ensureIncidentExists(String tenantId, String incidentId) {
    if (repository.findIncident(tenantId, incidentId).isEmpty()) {
      throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
    }
  }

  private void validateStatusTransition(
      String tenantId, IncidentSummaryRecord current, String targetStatus) {
    if (!ACTIVE_STATUSES.contains(targetStatus)) {
      return;
    }

    if (current.aggregationKey() == null || current.aggregationKey().isBlank()) {
      return;
    }

    repository
        .findActiveIncidentByAggregationKey(tenantId, current.aggregationKey())
        .filter(active -> !active.id().equals(current.id()))
        .ifPresent(
            active -> {
              throw new AppException(
                  "INCIDENT_ACTIVE_CONFLICT",
                  "Another active incident already exists for the same aggregation key");
            });
  }

  private String normalizeStatus(String status) {
    if (status == null || status.isBlank()) {
      throw new AppException("INCIDENT_STATUS_INVALID", "Incident status is required");
    }

    String normalized = status.trim().toLowerCase(Locale.ROOT);
    if (!ALLOWED_STATUSES.contains(normalized)) {
      throw new AppException("INCIDENT_STATUS_INVALID", "Unsupported incident status: " + status);
    }

    return normalized;
  }

  private String alertPayload(AlertCandidate alert, String aggregationKey) {
    return """
                {
                  "alertId": "%s",
                  "source": "%s",
                  "sourceEventId": "%s",
                  "fingerprint": "%s",
                  "aggregationKey": "%s"
                }
                """
        .formatted(
            escapeJson(alert.id()),
            escapeJson(alert.source()),
            escapeJson(alert.sourceEventId()),
            escapeJson(alert.fingerprint()),
            escapeJson(aggregationKey));
  }

  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }

    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private String newId(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }
}
