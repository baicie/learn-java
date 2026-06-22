package io.aegisops.integration.zabbix;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ZabbixWebhookAlertMapping(
    String datasourceId,
    String sourceEventId,
    List<String> hostIds,
    String severity,
    String title,
    String description,
    String entityType,
    String entityName,
    Map<String, Object> labels,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String status,
    Object rawPayload,
    String fingerprint,
    String aggregationKey) {}
