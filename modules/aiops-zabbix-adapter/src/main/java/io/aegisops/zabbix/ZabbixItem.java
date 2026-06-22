package io.aegisops.zabbix;

import java.util.Map;

public record ZabbixItem(
    String itemId,
    String hostId,
    String name,
    String key,
    int valueType,
    String units,
    String type,
    String delay,
    Map<String, String> tags,
    Map<String, Object> raw) {}
