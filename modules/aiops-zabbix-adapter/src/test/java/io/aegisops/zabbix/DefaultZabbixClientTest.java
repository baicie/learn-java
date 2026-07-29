package io.aegisops.zabbix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DefaultZabbixClientTest {

  private static final ZabbixConfig CONFIG =
      new ZabbixConfig("http://zabbix.test/api_jsonrpc.php", "Admin", "zabbix", null, 1, 5);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private DefaultZabbixClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);
    client = new DefaultZabbixClient(CONFIG, objectMapper, restTemplate);
  }

  @Test
  void rootEndpointUsesStandardJsonRpcPath() {
    ZabbixConfig rootConfig = new ZabbixConfig("https://zabbix.test/", null, null, "token", 1, 5);
    client = new DefaultZabbixClient(rootConfig, objectMapper, restTemplate);

    server
        .expect(requestTo("https://zabbix.test/api_jsonrpc.php"))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"7.0.0\",\"id\":1}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("https://zabbix.test/api_jsonrpc.php"))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":[],\"id\":2}", MediaType.APPLICATION_JSON));

    assertEquals("7.0.0", client.testConnection());
  }

  @Test
  void testConnectionReturnsApiVersion() {
    // testConnection: apiinfo.version (no auth) -> user.login -> host.get (limit=1).
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"7.0.0\",\"id\":1}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"sess-1\",\"id\":2}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andExpect(method(org.springframework.http.HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":[{\"hostid\":\"1\"}],\"id\":3}",
                MediaType.APPLICATION_JSON));
    assertEquals("7.0.0", client.testConnection());
  }

  @Test
  void testConnectionRejectsBadCredentials() {
    // apiinfo.version is anonymous and always succeeds; a bad token is only
    // detected by the follow-up authenticated host.get.
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"7.0.0\",\"id\":1}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"sess-1\",\"id\":2}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32602,\"message\":\"Not authorized.\"},\"id\":3}",
                MediaType.APPLICATION_JSON));
    ZabbixApiException ex = assertThrows(ZabbixApiException.class, () -> client.testConnection());
    assertTrue(
        ex.getMessage().toLowerCase().contains("not authorized"),
        "expected auth rejection to surface, was: " + ex.getMessage());
  }

  @Test
  void apiErrorBecomesZabbixApiException() {
    // First request is user.login (auth). We need a successful login to get past authToken(),
    // then the second request (host.get) is the one that returns the API error.
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"sess-1\",\"id\":1}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32602,\"message\":\"Invalid params.\",\"data\":\"username field not supported\"},\"id\":2}",
                MediaType.APPLICATION_JSON));
    ZabbixApiException ex = assertThrows(ZabbixApiException.class, () -> client.getHosts(10));
    assertTrue(
        ex.getMessage().toLowerCase().contains("invalid params"),
        "expected server error message in exception, was: " + ex.getMessage());
  }

  @Test
  void authErrorFromNewerZabbixIsNotSwallowedAndRetried() {
    // getHosts -> authToken() -> login("username") -> Zabbix rejects with an AUTH error.
    // That error must propagate; the client must NOT retry with field=user (which would
    // produce a misleading "not supported" message that hides the real auth failure).
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32602,\"message\":\"Login name or password is incorrect.\"},\"id\":1}",
                MediaType.APPLICATION_JSON));
    ZabbixApiException ex = assertThrows(ZabbixApiException.class, () -> client.getHosts(10));
    assertTrue(
        ex.getMessage().toLowerCase().contains("login name or password is incorrect"),
        "expected raw auth error, was: " + ex.getMessage());
  }

  @Test
  void legacyFieldErrorTriggersFallbackLoginField() {
    // First call (username field) -> server says "not supported".
    // Second call (user field)    -> server returns a token.
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32602,\"message\":\"Unsupported parameter: username\"},\"id\":1}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"legacy-auth-token\",\"id\":2}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":[{\"hostid\":\"1010\",\"host\":\"web-01\",\"name\":\"web-01\",\"status\":\"0\",\"interfaces\":[{\"ip\":\"10.0.0.1\",\"dns\":\"web-01.local\",\"main\":\"1\"}],\"groups\":[{\"groupid\":\"2\",\"name\":\"Web\"}]}],\"id\":3}",
                MediaType.APPLICATION_JSON));
    List<ZabbixHost> hosts = client.getHosts(10);
    assertEquals(1, hosts.size());
    assertEquals("web-01", hosts.get(0).host());
    assertEquals("10.0.0.1", hosts.get(0).ip());
  }

  @Test
  void getHostsReadsMachineIdentityFromInventory() {
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"token\",\"id\":1}", MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("selectInventory")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("selectTags")))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":[{\"hostid\":\"1010\",\"host\":\"web-01\",\"name\":\"web-01\",\"status\":\"0\",\"interfaces\":[],\"groups\":[],\"inventory\":{\"serialno_a\":\"machine-001\"},\"tags\":[]}],\"id\":2}",
                MediaType.APPLICATION_JSON));

    List<ZabbixHost> hosts = client.getHosts(10);

    assertEquals("machine-001", hosts.getFirst().machineId());
  }

  @Test
  void getProblemsRequestsAndParsesTagsAndRecoveryMetadata() {
    // Zabbix 7.x: problem.get returns no hosts; client does a follow-up
    // event.get(filter.value=1) to back-fill hostids. Each problem triggers
    // its own event.get so the mock below is intentionally simple.
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"legacy-auth-token\",\"id\":1}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"selectTags\":\"extend\"")))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":[{\"eventid\":\"99\",\"objectid\":\"500\",\"name\":\"CPU high\",\"severity\":\"4\",\"clock\":\"1700000000\",\"r_eventid\":\"100\",\"r_clock\":\"1700000300\",\"tags\":[{\"tag\":\"env\",\"value\":\"prod\"}]}],\"id\":2}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":[{\"eventid\":\"99\",\"hosts\":[{\"hostid\":\"7\"}]}],\"id\":3}",
                MediaType.APPLICATION_JSON));
    List<ZabbixProblem> problems = client.getProblems(10);
    assertEquals(1, problems.size());
    ZabbixProblem p = problems.get(0);
    assertEquals("99", p.eventId());
    assertEquals("500", p.objectId());
    assertEquals(4, p.severity());
    assertEquals("CPU high", p.name());
    assertEquals(List.of("7"), p.hostIds());
    assertEquals(Map.of("env", "prod"), p.tags());
    assertEquals("100", p.recoveryEventId());
    assertEquals(Instant.ofEpochSecond(1700000300), p.recoveryClock());
    assertTrue(p.recovered());
  }

  @Test
  void httpServerErrorIsWrapped() {
    // user.login succeeds with a token, then host.get returns 500
    server
        .expect(requestTo(CONFIG.endpoint()))
        .andRespond(
            withSuccess(
                "{\"jsonrpc\":\"2.0\",\"result\":\"sess-1\",\"id\":1}",
                MediaType.APPLICATION_JSON));
    server.expect(requestTo(CONFIG.endpoint())).andRespond(withServerError());
    ZabbixApiException ex = assertThrows(ZabbixApiException.class, () -> client.getHosts(10));
    assertTrue(
        ex.getMessage().toLowerCase().contains("failed to call zabbix api"),
        "expected wrapped HTTP error, was: " + ex.getMessage());
  }
}
