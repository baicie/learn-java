package io.aegisops.incident;

import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIncidentRepository implements IncidentRepository {
  private final JdbcTemplate jdbc;

  public JdbcIncidentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void acquireTenantAggregationLock(String tenantId) {
    /*
     * Serialize incident aggregation per tenant.
     *
     * Prevents two concurrent POST /api/incidents/aggregate requests from:
     * 1. reading the same unlinked alert_event rows,
     * 2. creating competing active incidents for the same aggregation_key,
     * 3. over-counting alert_count,
     * 4. writing duplicate timeline rows.
     *
     * pg_advisory_xact_lock is transaction-scoped and will be released
     * automatically when the @Transactional aggregateOpenAlerts ends.
     */
    jdbc.queryForList(
        "select pg_advisory_xact_lock(hashtext('incident_aggregate'), hashtext(?))", tenantId);
  }

  @Override
  public List<AlertCandidate> findOpenAlertCandidates(
      String tenantId, OffsetDateTime since, int limit) {
    return jdbc.query(
        """
                select id, tenant_id, source, source_event_id, severity, title, description,
                       asset_id, entity_type, entity_name, fingerprint, aggregation_key,
                       labels::text as labels_json, starts_at, ends_at, created_at
                from alert_event a
                where tenant_id = ?
                  and status = 'open'
                  and starts_at >= ?
                  and not exists (
                    select 1 from incident_event ie
                    where ie.event_type = 'alert'
                      and ie.event_id = a.id
                  )
                order by starts_at asc
                limit ?
                """,
        (rs, rowNum) ->
            new AlertCandidate(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getString("aggregation_key"),
                rs.getString("labels_json"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)),
        tenantId,
        since,
        limit);
  }

  @Override
  public List<IncidentSummaryRecord> findActiveIncidentsReadyToResolve(String tenantId) {
    return jdbc.query(
        """
                select i.id, i.tenant_id, i.title, i.summary, i.severity, i.status, i.source,
                       i.primary_asset_id, i.aggregation_key, i.alert_count, i.impact_score,
                       i.started_at, i.detected_at, i.last_seen_at, i.resolved_at,
                       i.created_at, i.updated_at
                from incident i
                where i.tenant_id = ?
                  and i.status in ('open', 'investigating', 'mitigating')
                  and exists (
                    select 1
                    from incident_event ie
                    join alert_event a on a.id = ie.event_id
                    where ie.incident_id = i.id
                      and ie.event_type = 'alert'
                  )
                  and not exists (
                    select 1
                    from incident_event ie
                    join alert_event a on a.id = ie.event_id
                    where ie.incident_id = i.id
                      and ie.event_type = 'alert'
                      and a.status = 'open'
                  )
                order by i.started_at asc
                """,
        (rs, rowNum) -> IncidentRows.summary(rs),
        tenantId);
  }

  @Override
  public List<AlertCandidate> listLinkedAlertCandidates(String tenantId, String incidentId) {
    ensureIncidentBelongsToTenant(tenantId, incidentId);

    return jdbc.query(
        """
                select a.id, a.tenant_id, a.source, a.source_event_id, a.severity, a.title,
                       a.description, a.asset_id, a.entity_type, a.entity_name, a.fingerprint,
                       a.aggregation_key, a.labels::text as labels_json, a.starts_at, a.ends_at,
                       a.created_at
                from incident_event ie
                join alert_event a on a.id = ie.event_id
                where ie.incident_id = ?
                  and ie.event_type = 'alert'
                order by a.starts_at asc
                """,
        (rs, rowNum) ->
            new AlertCandidate(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("asset_id"),
                rs.getString("entity_type"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getString("aggregation_key"),
                rs.getString("labels_json"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class)),
        incidentId);
  }

  @Override
  public List<IncidentSummaryRecord> listIncidents(String tenantId, int limit) {
    return jdbc.query(
        """
                select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                       aggregation_key, alert_count, impact_score, started_at, detected_at,
                       last_seen_at, resolved_at, created_at, updated_at
                from incident
                where tenant_id = ?
                order by started_at desc
                limit ?
                """,
        (rs, rowNum) -> IncidentRows.summary(rs),
        tenantId,
        limit);
  }

  @Override
  public Optional<IncidentSummaryRecord> findIncident(String tenantId, String incidentId) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, impact_score, started_at, detected_at,
                           last_seen_at, resolved_at, created_at, updated_at
                    from incident
                    where tenant_id = ? and id = ?
                    """,
              (rs, rowNum) -> IncidentRows.summary(rs),
              tenantId,
              incidentId));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<IncidentSummaryRecord> findActiveIncidentByAggregationKey(
      String tenantId, String aggregationKey) {
    try {
      return Optional.ofNullable(
          jdbc.queryForObject(
              """
                    select id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                           aggregation_key, alert_count, impact_score, started_at, detected_at,
                           last_seen_at, resolved_at, created_at, updated_at
                    from incident
                    where tenant_id = ?
                      and aggregation_key = ?
                      and status in ('open', 'investigating', 'mitigating')
                    order by started_at desc
                    limit 1
                    """,
              (rs, rowNum) -> IncidentRows.summary(rs),
              tenantId,
              aggregationKey));
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }

  @Override
  public void insertIncident(IncidentCreateCommand command) {
    jdbc.update(
        """
                insert into incident(id, tenant_id, title, summary, severity, status, source, primary_asset_id,
                                     aggregation_key, alert_count, impact_score, started_at, detected_at,
                                     last_seen_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'open', ?, ?, ?, ?, 0, ?, ?, ?, now(), now())
                """,
        command.id(),
        command.tenantId(),
        command.title(),
        command.summary(),
        command.severity(),
        command.source(),
        command.primaryAssetId(),
        command.aggregationKey(),
        command.alertCount(),
        command.startedAt(),
        command.detectedAt(),
        command.lastSeenAt());
  }

  @Override
  public void updateIncidentAggregation(IncidentUpdateCommand cmd) {
    jdbc.update(
        """
                update incident
                set title = ?,
                    summary = ?,
                    severity = ?,
                    alert_count = ?,
                    last_seen_at = ?,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """,
        cmd.title(),
        cmd.summary(),
        cmd.severity(),
        cmd.alertCount(),
        cmd.lastSeenAt(),
        cmd.tenantId(),
        cmd.incidentId());
  }

  @Override
  public boolean linkAlert(
      String id,
      String incidentId,
      String alertId,
      String relationType,
      OffsetDateTime occurredAt) {
    int updated =
        jdbc.update(
            """
                insert into incident_event(id, incident_id, event_type, event_id, relation_type, occurred_at)
                values (?, ?, 'alert', ?, ?, ?)
                on conflict do nothing
                """,
            id,
            incidentId,
            alertId,
            relationType,
            occurredAt);

    return updated > 0;
  }

  @Override
  public void addTimeline(TimelineCreateCommand command) {
    jdbc.update(
        """
                insert into incident_timeline(id, incident_id, event_time, event_type, title, description, source, payload)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
        command.id(),
        command.incidentId(),
        command.eventTime(),
        command.eventType(),
        command.title(),
        command.description(),
        command.source(),
        command.payloadJson());
  }

  @Override
  public List<IncidentAlertRecord> listIncidentAlerts(String tenantId, String incidentId) {
    ensureIncidentBelongsToTenant(tenantId, incidentId);

    return jdbc.query(
        """
                select a.id, a.source, a.source_event_id, a.severity, a.title, a.status,
                       a.asset_id, a.entity_name, a.fingerprint, a.starts_at, ie.relation_type
                from incident_event ie
                join alert_event a on a.id = ie.event_id
                where ie.incident_id = ?
                  and ie.event_type = 'alert'
                order by a.starts_at asc
                """,
        (rs, rowNum) ->
            new IncidentAlertRecord(
                rs.getString("id"),
                rs.getString("source"),
                rs.getString("source_event_id"),
                rs.getString("severity"),
                rs.getString("title"),
                rs.getString("status"),
                rs.getString("asset_id"),
                rs.getString("entity_name"),
                rs.getString("fingerprint"),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getString("relation_type")),
        incidentId);
  }

  @Override
  public List<IncidentTimelineRecord> listTimeline(String tenantId, String incidentId) {
    ensureIncidentBelongsToTenant(tenantId, incidentId);

    return jdbc.query(
        """
                select id, event_time, event_type, title, description, source, payload::text
                from incident_timeline
                where incident_id = ?
                order by event_time asc
                """,
        (rs, rowNum) ->
            new IncidentTimelineRecord(
                rs.getString("id"),
                rs.getObject("event_time", OffsetDateTime.class),
                rs.getString("event_type"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("source"),
                rs.getString("payload")),
        incidentId);
  }

  @Override
  public int countLinkedAlerts(String incidentId) {
    Integer count =
        jdbc.queryForObject(
            """
                select count(*)
                from incident_event
                where incident_id = ?
                  and event_type = 'alert'
                """,
            Integer.class,
            incidentId);

    return count == null ? 0 : count;
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
    OffsetDateTime effectiveResolvedAt = resolvedAt == null ? OffsetDateTime.now() : resolvedAt;

    int updated =
        jdbc.update(
            """
                update incident
                set status = ?,
                    resolved_at = case when ? then coalesce(resolved_at, ?) else null end,
                    updated_at = now()
                where tenant_id = ? and id = ?
                """,
            status,
            terminal,
            effectiveResolvedAt,
            tenantId,
            incidentId);

    if (updated == 0) {
      throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
    }
  }

  private void ensureIncidentBelongsToTenant(String tenantId, String incidentId) {
    Boolean exists =
        jdbc.queryForObject(
            """
                select exists(select 1 from incident where tenant_id = ? and id = ?)
                """,
            Boolean.class,
            tenantId,
            incidentId);

    if (!Boolean.TRUE.equals(exists)) {
      throw new AppException("INCIDENT_NOT_FOUND", "Incident not found");
    }
  }
}
