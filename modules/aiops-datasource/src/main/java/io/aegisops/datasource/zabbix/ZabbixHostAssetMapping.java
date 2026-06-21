package io.aegisops.datasource.zabbix;

import java.util.Map;

public record ZabbixHostAssetMapping(
    String sourceId,
    String name,
    String displayName,
    String ip,
    Map<String, Object> tags,
    String status) {}
