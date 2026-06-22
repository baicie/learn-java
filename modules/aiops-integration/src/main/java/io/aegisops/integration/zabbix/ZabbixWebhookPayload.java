package io.aegisops.integration.zabbix;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.time.OffsetDateTime;
import java.util.Map;

public record ZabbixWebhookPayload(
    @JsonAlias({"datasource_id", "datasource"}) String datasourceId,
    @JsonAlias({"event_id", "eventid"}) String eventId,
    @JsonAlias({"problem_id", "problemEventId", "problem_event_id"}) String problemId,
    @JsonAlias({"recovery_event_id", "recoveryEventId"}) String recoveryEventId,
    @JsonAlias({"trigger_id", "triggerid"}) String triggerId,
    @JsonAlias({"object_id", "objectid"}) String objectId,
    @JsonAlias({"event_value", "eventValue"}) String eventValue,
    String status,
    String severity,
    @JsonAlias({"name", "title", "subject"}) String title,
    String message,
    @JsonAlias({"host_id", "hostid"}) String hostId,
    @JsonAlias({"host_name", "hostname"}) String hostName,
    String host,
    String app,
    String env,
    String environment,
    String service,
    String component,
    String endpoint,
    String url,
    @JsonAlias({"starts_at", "startTime", "event_time"}) OffsetDateTime startsAt,
    @JsonAlias({"ends_at", "recovery_time"}) OffsetDateTime endsAt,
    @JsonAlias({"clock", "event_clock"}) Long clock,
    Map<String, String> tags) {}
