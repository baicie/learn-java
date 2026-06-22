package io.aegisops.datasource.zabbix;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ZabbixAlertEventMapping(
    String sourceEventId,
    List<String> hostIds,
    String severity,
    String title,
    String description,
    String entityType,
    String entityName,
    Map<String, Object> labels,
    OffsetDateTime startsAt,
    String status,
    Object rawPayload,
    String fingerprint,
    String aggregationKey) {}
