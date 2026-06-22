package io.aegisops.zabbix;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ZabbixEvent(
    String eventId,
    String objectId,
    String name,
    String severity,
    String value,
    Instant clock,
    List<String> hostIds,
    Map<String, String> tags,
    Map<String, Object> raw) {}
