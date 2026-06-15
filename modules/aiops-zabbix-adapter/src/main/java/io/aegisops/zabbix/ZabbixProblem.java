package io.aegisops.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ZabbixProblem(
    String eventId,
    String objectId,
    String name,
    int severity,
    Instant clock,
    List<String> hostIds,
    Map<String, String> tags,
    JsonNode raw) {}
