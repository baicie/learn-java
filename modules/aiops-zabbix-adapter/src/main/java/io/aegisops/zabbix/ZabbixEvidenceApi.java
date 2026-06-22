package io.aegisops.zabbix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper that owns the JSON-RPC calls for Zabbix evidence collection (item / history / trend /
 * event / trigger). Kept separate from {@link DefaultZabbixClient} so the latter stays under the
 * 500-line Checkstyle limit.
 */
final class ZabbixEvidenceApi {
  private final ObjectMapper objectMapper;

  ZabbixEvidenceApi(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  List<ZabbixItem> getItems(JsonRpcCaller caller, ZabbixItemQuery query) {
    String auth = caller.authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", "extend");
    params.put("selectTags", "extend");
    params.put("sortfield", "name");
    params.put("limit", query == null ? 500 : query.normalizedLimit());

    if (query != null && query.hostIds() != null && !query.hostIds().isEmpty()) {
      params.put("hostids", query.hostIds());
    }

    Map<String, Object> search = new LinkedHashMap<>();
    if (query != null && query.keySearch() != null && !query.keySearch().isBlank()) {
      search.put("key_", query.keySearch().trim());
    }
    if (query != null && query.nameSearch() != null && !query.nameSearch().isBlank()) {
      search.put("name", query.nameSearch().trim());
    }
    if (!search.isEmpty()) {
      params.put("search", search);
      params.put("searchByAny", true);
    }

    JsonNode result = caller.call("item.get", params, auth);
    List<ZabbixItem> items = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        items.add(toItem(item));
      }
    }

    return items;
  }

  List<ZabbixHistoryPoint> getHistory(JsonRpcCaller caller, ZabbixHistoryQuery query) {
    if (query == null || query.itemIds() == null || query.itemIds().isEmpty()) {
      return List.of();
    }

    String auth = caller.authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", "extend");
    params.put("history", query.valueType());
    params.put("itemids", query.itemIds());
    params.put("sortfield", "clock");
    params.put("sortorder", "ASC");
    params.put("limit", query.normalizedLimit());

    if (query.timeFrom() != null) {
      params.put("time_from", query.timeFrom().getEpochSecond());
    }
    if (query.timeTill() != null) {
      params.put("time_till", query.timeTill().getEpochSecond());
    }

    JsonNode result = caller.call("history.get", params, auth);
    List<ZabbixHistoryPoint> points = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        points.add(toHistoryPoint(item, query.valueType()));
      }
    }

    return points;
  }

  List<ZabbixTrendPoint> getTrends(JsonRpcCaller caller, ZabbixTrendQuery query) {
    if (query == null || query.itemIds() == null || query.itemIds().isEmpty()) {
      return List.of();
    }

    String auth = caller.authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", "extend");
    params.put("itemids", query.itemIds());
    params.put("sortfield", "clock");
    params.put("sortorder", "ASC");
    params.put("limit", query.normalizedLimit());

    if (query.timeFrom() != null) {
      params.put("time_from", query.timeFrom().getEpochSecond());
    }
    if (query.timeTill() != null) {
      params.put("time_till", query.timeTill().getEpochSecond());
    }

    JsonNode result = caller.call("trend.get", params, auth);
    List<ZabbixTrendPoint> points = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        points.add(toTrendPoint(item));
      }
    }

    return points;
  }

  List<ZabbixEvent> getEvents(JsonRpcCaller caller, ZabbixEventQuery query) {
    String auth = caller.authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", "extend");
    params.put("selectHosts", List.of("hostid"));
    params.put("selectTags", "extend");
    params.put("sortfield", List.of("clock", "eventid"));
    params.put("sortorder", "ASC");
    params.put("limit", query == null ? 100 : query.normalizedLimit());

    if (query != null && query.eventIds() != null && !query.eventIds().isEmpty()) {
      params.put("eventids", query.eventIds());
    }
    if (query != null && query.hostIds() != null && !query.hostIds().isEmpty()) {
      params.put("hostids", query.hostIds());
    }
    if (query != null && query.objectIds() != null && !query.objectIds().isEmpty()) {
      params.put("objectids", query.objectIds());
    }
    if (query != null && query.timeFrom() != null) {
      params.put("time_from", query.timeFrom().getEpochSecond());
    }
    if (query != null && query.timeTill() != null) {
      params.put("time_till", query.timeTill().getEpochSecond());
    }

    JsonNode result = caller.call("event.get", params, auth);
    List<ZabbixEvent> events = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        events.add(toEvent(item));
      }
    }

    return events;
  }

  List<ZabbixTrigger> getTriggers(JsonRpcCaller caller, ZabbixTriggerQuery query) {
    String auth = caller.authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", "extend");
    params.put("selectHosts", List.of("hostid"));
    params.put("selectTags", "extend");
    params.put("sortfield", "description");
    params.put("limit", query == null ? 100 : query.normalizedLimit());

    if (query != null && query.hostIds() != null && !query.hostIds().isEmpty()) {
      params.put("hostids", query.hostIds());
    }
    if (query != null && query.triggerIds() != null && !query.triggerIds().isEmpty()) {
      params.put("triggerids", query.triggerIds());
    }
    if (query != null
        && query.descriptionSearch() != null
        && !query.descriptionSearch().isBlank()) {
      params.put("search", Map.of("description", query.descriptionSearch().trim()));
    }

    JsonNode result = caller.call("trigger.get", params, auth);
    List<ZabbixTrigger> triggers = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        triggers.add(toTrigger(item));
      }
    }

    return triggers;
  }

  ZabbixItem toItem(JsonNode item) {
    return new ZabbixItem(
        item.path("itemid").asText(null),
        item.path("hostid").asText(null),
        item.path("name").asText(null),
        item.path("key_").asText(null),
        item.path("value_type").asInt(0),
        item.path("units").asText(null),
        item.path("type").asText(null),
        item.path("delay").asText(null),
        tags(item.path("tags")),
        rawMap(item));
  }

  ZabbixHistoryPoint toHistoryPoint(JsonNode item, int valueType) {
    long clock = item.path("clock").asLong(0);
    return new ZabbixHistoryPoint(
        item.path("itemid").asText(null),
        valueType,
        clock > 0 ? Instant.ofEpochSecond(clock) : Instant.EPOCH,
        item.path("value").asText(null),
        rawMap(item));
  }

  ZabbixTrendPoint toTrendPoint(JsonNode item) {
    long clock = item.path("clock").asLong(0);
    return new ZabbixTrendPoint(
        item.path("itemid").asText(null),
        clock > 0 ? Instant.ofEpochSecond(clock) : Instant.EPOCH,
        item.path("value_min").asDouble(0),
        item.path("value_avg").asDouble(0),
        item.path("value_max").asDouble(0),
        item.path("num").asLong(0),
        rawMap(item));
  }

  ZabbixEvent toEvent(JsonNode item) {
    long clock = item.path("clock").asLong(0);
    return new ZabbixEvent(
        item.path("eventid").asText(null),
        item.path("objectid").asText(null),
        item.path("name").asText(null),
        item.path("severity").asText(null),
        item.path("value").asText(null),
        clock > 0 ? Instant.ofEpochSecond(clock) : Instant.EPOCH,
        hostIds(item.path("hosts")),
        tags(item.path("tags")),
        rawMap(item));
  }

  ZabbixTrigger toTrigger(JsonNode item) {
    return new ZabbixTrigger(
        item.path("triggerid").asText(null),
        item.path("description").asText(null),
        item.path("expression").asText(null),
        item.path("priority").asText(null),
        item.path("value").asText(null),
        hostIds(item.path("hosts")),
        tags(item.path("tags")),
        rawMap(item));
  }

  private List<String> hostIds(JsonNode hosts) {
    if (!hosts.isArray()) {
      return List.of();
    }

    List<String> out = new ArrayList<>();
    for (JsonNode host : hosts) {
      String hostId = host.path("hostid").asText(null);
      if (hostId != null && !hostId.isBlank()) {
        out.add(hostId);
      }
    }
    return List.copyOf(out);
  }

  private Map<String, String> tags(JsonNode tags) {
    if (!tags.isArray()) {
      return Map.of();
    }

    Map<String, String> out = new LinkedHashMap<>();
    for (JsonNode tag : tags) {
      String key = tag.path("tag").asText(null);
      String value = tag.path("value").asText(null);
      if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
        out.put(key, value);
      }
    }
    return Map.copyOf(out);
  }

  private Map<String, Object> rawMap(JsonNode node) {
    return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
  }
}
