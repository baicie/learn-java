package io.aegisops.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ZabbixHost(
    String hostId,
    String host,
    String name,
    String status,
    String ip,
    List<String> groups,
    String machineId,
    JsonNode raw) {}
