package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IncidentService {
    private static final List<String> ALLOWED_STATUSES = List.of(
            "open",
            "investigating",
            "mitigating",
            "resolved",
            "closed",
            "ignored"
    );

    private static final List<String> TERMINAL_STATUSES = List.of(
            "resolved",
            "closed",
            "ignored"
    );

    private final IncidentRepository repository;
    private final IncidentAggregationPolicy policy;

    public IncidentService(IncidentRepository repository, IncidentAggregationPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public List<IncidentRecord> list(String tenantId) {
        return repository.listIncidents(tenantId, 100)
                .stream()
                .map(IncidentRecord::from)
                .toList();
    }

    public IncidentDetailRecord detail(String tenantId, String incidentId) {
        IncidentRecord incident = repository.findIncident(tenantId, incidentId)
                .map(IncidentRecord::from)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));

        return new IncidentDetailRecord(
                incident,
                repository.listIncidentAlerts(tenantId, incidentId),
                repository.listTimeline(tenantId, incidentId)
        );
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
    public IncidentAggregationResponse aggregateOpenAlerts(String tenantId, IncidentAggregateRequest request) {
        IncidentAggregateRequest normalizedRequest = request == null ? new IncidentAggregateRequest(null, null) : request;

        OffsetDateTime since = OffsetDateTime.now().minusMinutes(normalizedRequest.normalizedWindowMinutes());
        List<AlertCandidate> candidates = repository.findOpenAlertCandidates(
                tenantId,
                since,
                normalizedRequest.normalizedLimit()
        );

        Map<String, List<AlertCandidate>> groups = candidates.stream()
                .collect(Collectors.groupingBy(
                        policy::aggregationKey,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        int incidentsCreated = 0;
        int incidentsUpdated = 0;
        int alertsLinked = 0;

        for (Map.Entry<String, List<AlertCandidate>> entry : groups.entrySet()) {
            String aggregationKey = entry.getKey();
            List<AlertCandidate> alerts = entry.getValue();

            if (alerts.isEmpty()) {
                continue;
            }

            var existingIncident = repository.findActiveIncidentByAggregationKey(tenantId, aggregationKey);
            String groupSeverity = policy.highestSeverity(alerts);
            String incidentId;
            int baseAlertCount;

            if (existingIncident.isPresent()) {
                IncidentSummaryRecord existing = existingIncident.get();
                incidentId = existing.id();
                baseAlertCount = existing.alertCount();
                incidentsUpdated++;
            } else {
                incidentId = newId("inc");
                baseAlertCount = 0;

                repository.insertIncident(new IncidentCreateCommand(
                        incidentId,
                        tenantId,
                        policy.title(aggregationKey, alerts),
                        policy.summary(aggregationKey, alerts),
                        groupSeverity,
                        "system",
                        policy.primaryAssetId(alerts),
                        aggregationKey,
                        alerts.size(),
                        policy.firstStartedAt(alerts),
                        OffsetDateTime.now(),
                        policy.lastSeenAt(alerts)
                ));

                incidentsCreated++;
            }

            int index = 0;
            for (AlertCandidate alert : alerts) {
                repository.linkAlert(
                        newId("ie"),
                        incidentId,
                        alert.id(),
                        index == 0 ? "primary" : "related",
                        alert.startsAt()
                );

                repository.addTimeline(new TimelineCreateCommand(
                        newId("tl"),
                        incidentId,
                        alert.startsAt(),
                        "alert_linked",
                        alert.title(),
                        alert.description(),
                        "system",
                        alertPayload(alert, aggregationKey)
                ));

                alertsLinked++;
                index++;
            }

            int actualAlertCount = baseAlertCount + alerts.size();
            String mergedSeverity = existingIncident
                    .map(existing -> IncidentSeverity.max(existing.severity(), groupSeverity))
                    .orElse(groupSeverity);

            repository.updateIncidentAggregation(
                    tenantId,
                    incidentId,
                    policy.title(aggregationKey, alerts),
                    policy.summary(aggregationKey, alerts),
                    mergedSeverity,
                    actualAlertCount,
                    policy.lastSeenAt(alerts)
            );
        }

        return new IncidentAggregationResponse(
                candidates.size(),
                groups.size(),
                incidentsCreated,
                incidentsUpdated,
                alertsLinked
        );
    }

    @Transactional
    public IncidentRecord updateStatus(String tenantId, String incidentId, IncidentStatusRequest request) {
        ensureIncidentExists(tenantId, incidentId);

        String status = normalizeStatus(request == null ? null : request.status());
        boolean terminal = TERMINAL_STATUSES.contains(status);

        repository.updateStatus(tenantId, incidentId, status, terminal);
        repository.addTimeline(new TimelineCreateCommand(
                newId("tl"),
                incidentId,
                OffsetDateTime.now(),
                "status_changed",
                "Incident status changed to " + status,
                request == null ? null : request.note(),
                "user",
                "{\"status\":\"" + status + "\"}"
        ));

        return repository.findIncident(tenantId, incidentId)
                .map(IncidentRecord::from)
                .orElseThrow(() -> new AppException("INCIDENT_NOT_FOUND", "Incident not found"));
    }

    public IncidentRecord resolve(String tenantId, String incidentId) {
        return updateStatus(tenantId, incidentId, new IncidentStatusRequest("resolved", "Resolved by user"));
    }

    public IncidentRecord close(String tenantId, String incidentId) {
        return updateStatus(tenantId, incidentId, new IncidentStatusRequest("closed", "Closed by user"));
    }

    private void ensureIncidentExists(String tenantId, String incidentId) {
        if (repository.findIncident(tenantId, incidentId).isEmpty()) {
            throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
        }
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
                """.formatted(
                escapeJson(alert.id()),
                escapeJson(alert.source()),
                escapeJson(alert.sourceEventId()),
                escapeJson(alert.fingerprint()),
                escapeJson(aggregationKey)
        );
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
