package io.aegisops.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboxWriterTest {

  @Test
  void returnsExistingRowForDuplicateIdempotencyKey() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
    when(jdbc.queryForObject(anyString(), eq(String.class), any(Object[].class)))
        .thenReturn("outbox_existing");
    OutboxWriter writer = new OutboxWriter(jdbc, new ObjectMapper());

    String id =
        writer.enqueue(
            new OutboxMessage(
                "tenant-1",
                "worker",
                "excel-export",
                Map.of("tenantId", "tenant-1"),
                "export:user-1:request-1",
                3,
                OffsetDateTime.parse("2026-07-14T10:00Z")));

    assertThat(id).isEqualTo("outbox_existing");
    verify(jdbc)
        .queryForObject(
            anyString(),
            eq(String.class),
            eq("worker"),
            eq("excel-export"),
            eq("export:user-1:request-1"),
            eq("tenant-1"));
  }
}
