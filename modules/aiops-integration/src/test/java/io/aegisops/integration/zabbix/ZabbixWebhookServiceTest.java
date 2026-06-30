package io.aegisops.integration.zabbix;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ZabbixWebhookServiceTest {

  @Test
  void shouldRejectInvalidTokenBeforeDatabaseAccess() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    io.aegisops.common.outbox.OutboxWriter outboxWriter =
        org.mockito.Mockito.mock(io.aegisops.common.outbox.OutboxWriter.class);
    ZabbixWebhookService service =
        new ZabbixWebhookService(
            jdbc,
            new ObjectMapper(),
            new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret")),
            new ZabbixWebhookMapper(),
            outboxWriter);

    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "ds_1",
            "20001",
            null,
            null,
            "30001",
            null,
            "1",
            "PROBLEM",
            "High",
            "CPU High",
            "CPU high",
            "10084",
            "host",
            null,
            "mall",
            "demo",
            null,
            "order-service",
            null,
            null,
            null,
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null,
            null,
            Map.of());

    assertThatThrownBy(() -> service.ingest("ds_1", "bad", payload))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Invalid Zabbix webhook token");

    verifyNoInteractions(jdbc);
  }
}
