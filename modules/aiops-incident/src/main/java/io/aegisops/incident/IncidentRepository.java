package io.aegisops.incident;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository {
  void acquireTenantAggregationLock(String tenantId);

  List<AlertCandidate> findOpenAlertCandidates(String tenantId, OffsetDateTime since, int limit);

  List<IncidentSummaryRecord> listIncidents(String tenantId, int limit);

  Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId);

  Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(
      String tenantId, String aggregationKey);

  void insertIncident(IncidentCreateCommand command);

  void updateIncidentAggregation(IncidentUpdateCommand command);

  boolean linkAlert(
      String id, String incidentId, String alertId, String relationType, OffsetDateTime occurredAt);

  void addTimeline(TimelineCreateCommand command);

  List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId);

  List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId);

  int countLinkedAlerts(String incidentId);

  void updateStatus(String tenantId, String incidentId, String status, boolean terminal);
}
