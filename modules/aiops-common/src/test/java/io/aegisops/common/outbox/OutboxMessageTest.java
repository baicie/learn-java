package io.aegisops.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

  @Test
  void appliesSafeDefaults() {
    OutboxMessage message = new OutboxMessage(null, "worker", "excel-export", null, null, 0, null);

    assertThat(message.payload()).isEmpty();
    assertThat(message.maxRetries()).isEqualTo(1);
    assertThat(message.availableAt()).isNotNull();
  }

  @Test
  void rejectsOversizedIdempotencyKeyBeforeDatabaseWrite() {
    assertThatThrownBy(
            () ->
                new OutboxMessage(
                    "tenant-1", "worker", "excel-export", Map.of(), "x".repeat(129), 3, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("idempotencyKey");
  }
}
