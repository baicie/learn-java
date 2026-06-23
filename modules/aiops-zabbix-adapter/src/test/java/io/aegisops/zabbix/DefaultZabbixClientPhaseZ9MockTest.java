package io.aegisops.zabbix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DefaultZabbixClientPhaseZ9MockTest {

  private MockRestServiceServer server;
  private DefaultZabbixClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);
    client =
        new DefaultZabbixClient(
            new ZabbixConfig("http://zabbix.local/api_jsonrpc.php", "Admin", "zabbix", null, 3, 3),
            new ObjectMapper(),
            restTemplate);
  }

  @Test
  void shouldFetchItemsFromMockZabbixApi() {
    server
        .expect(content().string(Matchers.containsString("\"method\":\"user.login\"")))
        .andRespond(withSuccess(LOGIN_RESULT, MediaType.APPLICATION_JSON));
    server
        .expect(content().string(Matchers.containsString("\"method\":\"item.get\"")))
        .andRespond(withSuccess(ITEMS_RESULT, MediaType.APPLICATION_JSON));

    List<ZabbixItem> items = client.getItems(new ZabbixItemQuery(List.of("10084"), null, null, 10));

    assertThat(items).hasSize(2);
    assertThat(items.get(0).key()).isEqualTo("demo.cpu.util");

    server.verify();
  }

  @Test
  void shouldFetchHistoryFromMockZabbixApi() {
    server
        .expect(content().string(Matchers.containsString("\"method\":\"user.login\"")))
        .andRespond(withSuccess(LOGIN_RESULT, MediaType.APPLICATION_JSON));
    server
        .expect(content().string(Matchers.containsString("\"method\":\"history.get\"")))
        .andRespond(withSuccess(HISTORY_RESULT, MediaType.APPLICATION_JSON));

    List<ZabbixHistoryPoint> history =
        client.getHistory(
            new ZabbixHistoryQuery(
                List.of("item_cpu"),
                0,
                Instant.parse("2026-06-21T05:00:00Z"),
                Instant.parse("2026-06-21T05:30:00Z"),
                100));

    assertThat(history).hasSize(2);
    assertThat(history.get(1).doubleValue()).hasValue(96.0);

    server.verify();
  }

  @Test
  void shouldFetchEventsFromMockZabbixApi() {
    server
        .expect(content().string(Matchers.containsString("\"method\":\"user.login\"")))
        .andRespond(withSuccess(LOGIN_RESULT, MediaType.APPLICATION_JSON));
    server
        .expect(content().string(Matchers.containsString("\"method\":\"event.get\"")))
        .andRespond(withSuccess(EVENTS_RESULT, MediaType.APPLICATION_JSON));

    List<ZabbixEvent> events =
        client.getEvents(
            new ZabbixEventQuery(
                List.of("20001"),
                List.of("10084"),
                List.of("30001"),
                Instant.parse("2026-06-21T05:00:00Z"),
                Instant.parse("2026-06-21T05:30:00Z"),
                100));

    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventId()).isEqualTo("20001");

    server.verify();
  }

  @Test
  void shouldFetchTriggersFromMockZabbixApi() {
    server
        .expect(content().string(Matchers.containsString("\"method\":\"user.login\"")))
        .andRespond(withSuccess(LOGIN_RESULT, MediaType.APPLICATION_JSON));
    server
        .expect(content().string(Matchers.containsString("\"method\":\"trigger.get\"")))
        .andRespond(withSuccess(TRIGGERS_RESULT, MediaType.APPLICATION_JSON));

    List<ZabbixTrigger> triggers =
        client.getTriggers(new ZabbixTriggerQuery(List.of("10084"), List.of("30001"), null, 100));

    assertThat(triggers).hasSize(1);
    assertThat(triggers.get(0).expression()).contains("demo.cpu.util");

    server.verify();
  }

  private static final String LOGIN_RESULT =
      """
      {
        "jsonrpc": "2.0",
        "result": "mock-token",
        "id": 1
      }
      """;

  private static final String ITEMS_RESULT =
      """
      {
        "jsonrpc": "2.0",
        "result": [
          {
            "itemid": "item_cpu",
            "hostid": "10084",
            "name": "AegisOps Demo CPU Utilization",
            "key_": "demo.cpu.util",
            "value_type": "0",
            "units": "%",
            "type": "19",
            "delay": "10s",
            "tags": [{"tag": "service", "value": "order-service"}]
          },
          {
            "itemid": "item_api",
            "hostid": "10084",
            "name": "AegisOps Demo Order Create Time",
            "key_": "demo.order.create.time",
            "value_type": "0",
            "units": "s",
            "type": "19",
            "delay": "10s",
            "tags": [{"tag": "service", "value": "order-service"}]
          }
        ],
        "id": 2
      }
      """;

  private static final String HISTORY_RESULT =
      """
      {
        "jsonrpc": "2.0",
        "result": [
          {"itemid": "item_cpu", "clock": "1782018600", "value": "20"},
          {"itemid": "item_cpu", "clock": "1782019200", "value": "96"}
        ],
        "id": 3
      }
      """;

  private static final String EVENTS_RESULT =
      """
      {
        "jsonrpc": "2.0",
        "result": [
          {
            "eventid": "20001",
            "objectid": "30001",
            "name": "CPU High",
            "severity": "4",
            "value": "1",
            "clock": "1782019200",
            "hosts": [{"hostid": "10084"}],
            "tags": [{"tag": "service", "value": "order-service"}]
          }
        ],
        "id": 4
      }
      """;

  private static final String TRIGGERS_RESULT =
      """
      {
        "jsonrpc": "2.0",
        "result": [
          {
            "triggerid": "30001",
            "description": "CPU High",
            "expression": "last(/aiops-demo-host/demo.cpu.util)>90",
            "priority": "4",
            "value": "1",
            "hosts": [{"hostid": "10084"}],
            "tags": [{"tag": "service", "value": "order-service"}]
          }
        ],
        "id": 5
      }
      """;
}
