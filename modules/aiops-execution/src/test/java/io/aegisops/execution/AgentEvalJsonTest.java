package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.exception.AppException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEvalJsonTest {
  private final AgentEvalJson json = new AgentEvalJson(new ObjectMapper());

  @Test
  void readStringListFiltersBlankAndDuplicates() {
    assertEquals(
        List.of("redis", "timeout"),
        json.readStringList("[\"redis\", \"\", \"timeout\", \"redis\"]"));
  }

  @Test
  void readStringListRejectsMalformedJson() {
    assertThrows(AppException.class, () -> json.readStringList("[not-json"));
  }

  @Test
  void readStringListRejectsObjectJson() {
    assertThrows(AppException.class, () -> json.readStringList("{\"keyword\":\"redis\"}"));
  }
}
