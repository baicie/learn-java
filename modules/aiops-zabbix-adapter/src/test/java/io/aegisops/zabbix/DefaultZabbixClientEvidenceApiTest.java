package io.aegisops.zabbix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class DefaultZabbixClientEvidenceApiTest {
  private MockRestServiceServer server;
  private DefaultZabbixClient client;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.createServer(restTemplate);

    client =
        new DefaultZabbixClient(
            new ZabbixConfig("http://zabbix/api_jsonrpc.php", "Admin", "zabbix", null, 3, 3),
            new ObjectMapper(),
            restTemplate);
  }

  @Test
  void shouldFetchItems() {
    expectLogin();
    server
        .expect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"item.get\"")))
        .andRespond(
            withSuccess(
                """
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "itemid": "1001",
                      "hostid": "10084",
                      "name": "AegisOps Demo CPU Utilization",
                      "key_": "demo.cpu.util",
                      "value_type": "0",
                      "units": "%",
                      "type": "19",
                      "delay": "10s",
                      "tags": [
                        {"tag": "service", "value": "order-service"}
                      ]
                    }
                  ],
                  "id": 2
                }
                """,
                MediaType.APPLICATION_JSON));

    List<ZabbixItem> items = client.getItems(new ZabbixItemQuery(List.of("10084"), null, null, 10));

    assertThat(items).hasSize(1);
    assertThat(items.get(0).itemId()).isEqualTo("1001");
    assertThat(items.get(0).key()).isEqualTo("demo.cpu.util");
    assertThat(items.get(0).tags()).containsEntry("service", "order-service");

    server.verify();
  }

  @Test
  void shouldFetchHistory() {
    expectLogin();
    server
        .expect(
            content().string(org.hamcrest.Matchers.containsString("\"method\":\"history.get\"")))
        .andRespond(
            withSuccess(
                """
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "itemid": "1001",
                      "clock": "1782000000",
                      "value": "95.5"
                    }
                  ],
                  "id": 2
                }
                """,
                MediaType.APPLICATION_JSON));

    List<ZabbixHistoryPoint> points =
        client.getHistory(
            new ZabbixHistoryQuery(
                List.of("1001"),
                0,
                Instant.parse("2026-06-21T05:00:00Z"),
                Instant.parse("2026-06-21T05:30:00Z"),
                100));

    assertThat(points).hasSize(1);
    assertThat(points.get(0).value()).isEqualTo("95.5");
    assertThat(points.get(0).doubleValue()).hasValue(95.5);

    server.verify();
  }

  @Test
  void shouldFetchEventsByObjectIds() {
    expectLogin();
    server
        .expect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"event.get\"")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"objectids\":[\"30001\"]")))
        .andRespond(
            withSuccess(
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
                      "clock": "1782000000",
                      "hosts": [{"hostid": "10084"}],
                      "tags": [{"tag": "service", "value": "order-service"}]
                    }
                  ],
                  "id": 2
                }
                """,
                MediaType.APPLICATION_JSON));

    List<ZabbixEvent> events =
        client.getEvents(
            new ZabbixEventQuery(
                null,
                List.of("10084"),
                List.of("30001"),
                Instant.parse("2026-06-21T05:00:00Z"),
                Instant.parse("2026-06-21T05:30:00Z"),
                100));

    assertThat(events).hasSize(1);
    assertThat(events.get(0).eventId()).isEqualTo("20001");
    assertThat(events.get(0).objectId()).isEqualTo("30001");
    assertThat(events.get(0).hostIds()).containsExactly("10084");
    assertThat(events.get(0).tags()).containsEntry("service", "order-service");

    server.verify();
  }

  @Test
  void shouldFetchTriggersByTriggerIds() {
    expectLogin();
    server
        .expect(
            content().string(org.hamcrest.Matchers.containsString("\"method\":\"trigger.get\"")))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("\"triggerids\":[\"30001\"]")))
        .andRespond(
            withSuccess(
                """
                {
                  "jsonrpc": "2.0",
                  "result": [
                    {
                      "triggerid": "30001",
                      "description": "CPU High",
                      "expression": "last(/host/demo.cpu.util)>80",
                      "priority": "4",
                      "value": "1",
                      "hosts": [{"hostid": "10084"}],
                      "tags": [{"tag": "service", "value": "order-service"}]
                    }
                  ],
                  "id": 2
                }
                """,
                MediaType.APPLICATION_JSON));

    List<ZabbixTrigger> triggers =
        client.getTriggers(new ZabbixTriggerQuery(List.of("10084"), List.of("30001"), null, 100));

    assertThat(triggers).hasSize(1);
    assertThat(triggers.get(0).triggerId()).isEqualTo("30001");
    assertThat(triggers.get(0).expression()).contains("demo.cpu.util");
    assertThat(triggers.get(0).hostIds()).containsExactly("10084");

    server.verify();
  }

  private void expectLogin() {
    server
        .expect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"user.login\"")))
        .andRespond(
            withSuccess(
                """
                {
                  "jsonrpc": "2.0",
                  "result": "token",
                  "id": 1
                }
                """,
                MediaType.APPLICATION_JSON));
  }
}
