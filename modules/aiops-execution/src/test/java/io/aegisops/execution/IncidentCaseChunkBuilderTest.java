package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentCaseChunkBuilderTest {
  @Test
  void buildChunksFromIncidentCase() {
    IncidentCaseChunkBuilder builder = new IncidentCaseChunkBuilder(new ObjectMapper());

    var chunks = builder.build(caseResponse());

    assertTrue(chunks.size() >= 3);
    assertTrue(chunks.get(0).content().contains("Root Cause"));
    assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("High error rate")));
    assertTrue(chunks.stream().anyMatch(chunk -> chunk.content().contains("Restart service")));
  }

  private IncidentCaseResponse caseResponse() {
    return new IncidentCaseResponse(
        "icase_1",
        "tenant_1",
        "pmr_1",
        "inc_1",
        "published",
        "high",
        "Order service db timeout",
        "Order service error rate increased",
        "db timeout",
        "Restart service",
        "Add timeout alert",
        80,
        "alice",
        "reviewer",
        OffsetDateTime.now(),
        null,
        List.of(
            new IncidentCaseSymptomResponse(
                "sym_1", "impact", "High error rate", "5xx increased", OffsetDateTime.now())),
        List.of(
            new IncidentCaseResolutionStepResponse(
                "step_1",
                1,
                "Restart service",
                "Restart order service",
                "manual",
                "pms_1",
                OffsetDateTime.now())),
        List.of("order-service", "db-timeout"),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
