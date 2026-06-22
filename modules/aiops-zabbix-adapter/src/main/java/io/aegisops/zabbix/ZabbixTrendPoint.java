package io.aegisops.zabbix;

import java.time.Instant;
import java.util.Map;

public record ZabbixTrendPoint(
    String itemId,
    Instant clock,
    double valueMin,
    double valueAvg,
    double valueMax,
    long count,
    Map<String, Object> raw) {}
