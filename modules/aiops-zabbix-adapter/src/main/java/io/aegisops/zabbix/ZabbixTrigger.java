package io.aegisops.zabbix;

import java.util.List;
import java.util.Map;

public record ZabbixTrigger(
    String triggerId,
    String description,
    String expression,
    String priority,
    String value,
    List<String> hostIds,
    Map<String, String> tags,
    Map<String, Object> raw) {}
