package io.aegisops.execution;

import io.aegisops.execution.dto.IncidentCaseResolutionStepResponse;
import io.aegisops.execution.dto.IncidentCaseResponse;
import io.aegisops.execution.dto.IncidentCaseSymptomResponse;
import java.time.OffsetDateTime;
import java.util.List;

final class KnowledgeBaseTestFixtures {
  private KnowledgeBaseTestFixtures() {}

  static IncidentCaseResponse caseResponse(String status) {
    return new IncidentCaseResponse(
        "icase_1",
        "tenant_1",
        "pmr_1",
        "inc_1",
        status,
        "high",
        "Order service redis timeout",
        "Order service failed due to redis timeout",
        "redis timeout",
        "restart order service and increase redis timeout",
        "add redis timeout alert",
        80,
        "alice",
        "reviewer",
        OffsetDateTime.now(),
        null,
        List.of(
            new IncidentCaseSymptomResponse(
                "sym_1",
                "impact",
                "High error rate",
                "order service 5xx increased",
                OffsetDateTime.now())),
        List.of(
            new IncidentCaseResolutionStepResponse(
                "step_1",
                1,
                "Restart service",
                "restart order service",
                "manual",
                "pms_1",
                OffsetDateTime.now())),
        List.of("order-service", "redis-timeout"),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
