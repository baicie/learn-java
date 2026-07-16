package io.aegisops.zabbix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

final class DefaultZabbixClient implements ZabbixClient, JsonRpcCaller {
  private final ZabbixConfig config;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final AtomicLong requestId = new AtomicLong(1);
  private final ZabbixEvidenceApi evidenceApi;

  DefaultZabbixClient(ZabbixConfig config, ObjectMapper objectMapper) {
    this(config, objectMapper, defaultRestTemplate(config));
  }

  DefaultZabbixClient(ZabbixConfig config, ObjectMapper objectMapper, RestTemplate restTemplate) {
    validate(config);
    this.config = config;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
    this.evidenceApi = new ZabbixEvidenceApi(objectMapper);
  }

  private static RestTemplate defaultRestTemplate(ZabbixConfig config) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(config.connectTimeoutMillis());
    requestFactory.setReadTimeout(config.readTimeoutMillis());
    return new RestTemplate(requestFactory);
  }

  @Override
  public String testConnection() {
    JsonNode version = call("apiinfo.version", Map.of(), null);

    /*
     * apiinfo.version does not prove that credentials are valid.
     * Validate auth by making a small authenticated host.get call.
     */
    String auth = authToken();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", List.of("hostid"));
    params.put("limit", 1);
    call("host.get", params, auth);

    return version.asText();
  }

  @Override
  public List<ZabbixHost> getHosts(int limit) {
    String auth = authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", List.of("hostid", "host", "name", "status"));
    params.put("selectInterfaces", List.of("ip", "dns", "main", "type"));
    params.put("selectGroups", List.of("groupid", "name"));
    params.put("selectInventory", "extend");
    params.put("selectTags", List.of("tag", "value"));
    params.put("sortfield", "name");
    params.put("limit", normalizeLimit(limit));

    JsonNode result = call("host.get", params, auth);
    List<ZabbixHost> hosts = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        hosts.add(toHost(item));
      }
    }

    return hosts;
  }

  @Override
  public List<ZabbixProblem> getProblems(int limit) {
    String auth = authToken();

    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", "extend");
    // Zabbix 7.x: problem.get has no selectHosts; use event.get with
    // filter.value=1 to back-fill hostids. Keep recent=true so we don't
    // hammer Zabbix with a global problem scan.
    params.put("recent", true);
    params.put("sortfield", List.of("eventid"));
    params.put("sortorder", "DESC");
    params.put("limit", normalizeLimit(limit));

    JsonNode result = call("problem.get", params, auth);
    List<ZabbixProblem> problems = new ArrayList<>();

    if (result.isArray()) {
      for (JsonNode item : result) {
        problems.add(toProblem(item, auth));
      }
    }

    return problems;
  }

  @Override
  public List<ZabbixItem> getItems(ZabbixItemQuery query) {
    return evidenceApi.getItems(this, query);
  }

  @Override
  public List<ZabbixHistoryPoint> getHistory(ZabbixHistoryQuery query) {
    return evidenceApi.getHistory(this, query);
  }

  @Override
  public List<ZabbixTrendPoint> getTrends(ZabbixTrendQuery query) {
    return evidenceApi.getTrends(this, query);
  }

  @Override
  public List<ZabbixEvent> getEvents(ZabbixEventQuery query) {
    return evidenceApi.getEvents(this, query);
  }

  @Override
  public List<ZabbixTrigger> getTriggers(ZabbixTriggerQuery query) {
    return evidenceApi.getTriggers(this, query);
  }

  private Map<String, List<String>> fetchHostIdsByEventId(String auth, List<String> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("output", List.of("eventid"));
    params.put("selectHosts", List.of("hostid"));
    params.put("eventids", eventIds);
    params.put("filter", Map.of("value", 1));
    JsonNode result = call("event.get", params, auth);
    Map<String, List<String>> out = new LinkedHashMap<>();
    if (result.isArray()) {
      for (JsonNode ev : result) {
        String eventId = ev.path("eventid").asText(null);
        if (eventId == null || eventId.isBlank()) {
          continue;
        }
        List<String> hostIds = new ArrayList<>();
        JsonNode hosts = ev.path("hosts");
        if (hosts.isArray()) {
          for (JsonNode host : hosts) {
            String hostId = host.path("hostid").asText(null);
            if (hostId != null && !hostId.isBlank()) {
              hostIds.add(hostId);
            }
          }
        }
        out.put(eventId, hostIds);
      }
    }
    return out;
  }

  private static void validate(ZabbixConfig config) {
    if (config == null || config.endpoint() == null || config.endpoint().isBlank()) {
      throw new ZabbixApiException("Zabbix endpoint is required");
    }

    if (!config.hasAuthentication()) {
      throw new ZabbixApiException("Zabbix username/password or apiToken is required");
    }
  }

  private int normalizeLimit(int limit) {
    if (limit <= 0) {
      return 100;
    }

    return Math.min(limit, 5000);
  }

  @Override
  public String authToken() {
    if (config.hasApiToken()) {
      return config.apiToken();
    }

    if (!config.hasUsernamePassword()) {
      throw new ZabbixApiException("Zabbix username/password or apiToken is required");
    }

    try {
      return login("username");
    } catch (ZabbixApiException ex) {
      if (isLegacyLoginFieldError(ex)) {
        return login("user");
      }

      throw ex;
    }
  }

  private static boolean isLegacyLoginFieldError(ZabbixApiException ex) {
    if (ex.getCause() != null) {
      return false;
    }

    String message = ex.getMessage();
    if (message == null) {
      return false;
    }

    String lower = message.toLowerCase();
    return lower.contains("unsupported parameter")
        || lower.contains("unknown method")
        || lower.contains("invalid params")
        || lower.contains("invalid parameter")
        || lower.contains("not supported");
  }

  private String login(String usernameField) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put(usernameField, config.username());
    params.put("password", config.password());

    JsonNode result = call("user.login", params, null);
    if (!result.isTextual()) {
      throw new ZabbixApiException("Zabbix login did not return a token");
    }

    return result.asText();
  }

  @Override
  public JsonNode call(String method, Object params, String auth) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("jsonrpc", "2.0");
      body.put("method", method);
      body.set("params", objectMapper.valueToTree(params == null ? Map.of() : params));

      if (auth != null && !auth.isBlank()) {
        body.put("auth", auth);
      }

      body.put("id", requestId.getAndIncrement());

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
      String response =
          restTemplate.exchange(config.endpoint(), HttpMethod.POST, entity, String.class).getBody();

      JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
      if (root.hasNonNull("error")) {
        JsonNode error = root.get("error");
        String message = error.path("message").asText("Zabbix API error");
        String data = error.path("data").asText("");
        throw new ZabbixApiException(data.isBlank() ? message : message + ": " + data);
      }

      if (!root.has("result")) {
        throw new ZabbixApiException("Zabbix API response missing result");
      }

      return root.path("result");
    } catch (ZabbixApiException ex) {
      throw ex;
    } catch (RestClientException ex) {
      throw new ZabbixApiException("Failed to call Zabbix API: " + ex.getMessage(), ex);
    } catch (Exception ex) {
      throw new ZabbixApiException("Failed to parse Zabbix API response", ex);
    }
  }

  private ZabbixHost toHost(JsonNode item) {
    String ip = null;
    JsonNode interfaces = item.path("interfaces");

    if (interfaces.isArray()) {
      for (JsonNode iface : interfaces) {
        if ("1".equals(iface.path("main").asText())) {
          ip = firstNonBlank(iface.path("ip").asText(null), iface.path("dns").asText(null));
          break;
        }
      }

      if (ip == null && interfaces.size() > 0) {
        JsonNode iface = interfaces.get(0);
        ip = firstNonBlank(iface.path("ip").asText(null), iface.path("dns").asText(null));
      }
    }

    List<String> groups = new ArrayList<>();
    JsonNode groupNodes = item.path("groups");

    if (groupNodes.isArray()) {
      for (JsonNode group : groupNodes) {
        String name = group.path("name").asText(null);
        if (name != null && !name.isBlank()) {
          groups.add(name);
        }
      }
    }

    return new ZabbixHost(
        item.path("hostid").asText(),
        item.path("host").asText(),
        item.path("name").asText(),
        item.path("status").asText("0"),
        ip,
        groups,
        machineId(item),
        item.deepCopy());
  }

  private String machineId(JsonNode item) {
    JsonNode tags = item.path("tags");
    if (tags.isArray()) {
      for (String accepted : List.of("machine_id", "machine.id", "host_uuid", "system_uuid")) {
        for (JsonNode tag : tags) {
          if (accepted.equalsIgnoreCase(tag.path("tag").asText())) {
            String value = tag.path("value").asText(null);
            if (value != null && !value.isBlank()) {
              return value.trim();
            }
          }
        }
      }
    }

    JsonNode inventory = item.path("inventory");
    for (String field : List.of("serialno_a", "asset_tag")) {
      String value = inventory.path(field).asText(null);
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private ZabbixProblem toProblem(JsonNode item, String auth) {
    String eventId = item.path("eventid").asText();
    Map<String, List<String>> hostMap = fetchHostIdsByEventId(auth, List.of(eventId));
    List<String> hostIds = hostMap.getOrDefault(eventId, List.of());

    Map<String, String> tags = new LinkedHashMap<>();
    JsonNode tagNodes = item.path("tags");

    if (tagNodes.isArray()) {
      for (JsonNode tag : tagNodes) {
        String key = tag.path("tag").asText(null);
        String value = tag.path("value").asText("");

        if (key != null && !key.isBlank()) {
          tags.put(key, value);
        }
      }
    }

    long epochSeconds = item.path("clock").asLong(Instant.now().getEpochSecond());

    return new ZabbixProblem(
        eventId,
        item.path("objectid").asText(),
        item.path("name").asText(),
        item.path("severity").asInt(0),
        Instant.ofEpochSecond(epochSeconds),
        hostIds,
        tags,
        item.deepCopy());
  }

  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }

    if (second != null && !second.isBlank()) {
      return second;
    }

    return null;
  }
}
