package io.aegisops.rum.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RumEventMapperTest {
  @Test
  void hashesUserAndKeepsTrace() {
    var json = new ObjectMapper();
    var event =
        new RumEventMapper(json)
            .map(
                json.createObjectNode()
                    .put("eventId", "e1")
                    .put("eventType", "error")
                    .put("page", "/checkout")
                    .put("sessionId", "s1")
                    .put("userId", "alice")
                    .put("errorMessage", "boom")
                    .put("traceId", "t1"));
    assertEquals(64, event.userHash().length());
    assertFalse(event.userHash().contains("alice"));
    assertEquals("t1", event.traceId());
  }

  @Test
  void rejectsWebVitalWithoutNameAndValue() {
    var json = new ObjectMapper();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RumEventMapper(json)
                .map(
                    json.createObjectNode()
                        .put("eventId", "e2")
                        .put("eventType", "web_vital")
                        .put("page", "/checkout")));
  }
}
