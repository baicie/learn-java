package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentCaseDraftBuilderTest {
  private final IncidentCaseDraftBuilder builder = new IncidentCaseDraftBuilder();

  @Test
  void buildDraftFromPostmortem() {
    IncidentCaseDraft draft =
        builder.build(
            report(),
            List.of(
                section("pms_1", "impact", "Impact", "High error rate"),
                section("pms_2", "detection", "Detection", "Alert fired"),
                section("pms_3", "resolution", "Resolution", "Restart service")),
            List.of(actionItem()),
            List.of("order-service", "db-timeout"));

    assertEquals("Postmortem - Order service error", draft.title());
    assertTrue(draft.symptoms().size() >= 2);
    assertTrue(draft.resolutionSteps().size() >= 2);
    assertTrue(draft.tags().contains("order-service"));
  }

  @Test
  void fallbackSymptomAndResolutionWhenSectionsEmpty() {
    IncidentCaseDraft draft = builder.build(report(), List.of(), List.of(), List.of());

    assertEquals(1, draft.symptoms().size());
    assertEquals(1, draft.resolutionSteps().size());
  }

  private PostmortemReportRecord report() {
    return new PostmortemReportRecord(
        "pmr_1",
        "tenant_1",
        "inc_1",
        "generated",
        "high",
        "Postmortem - Order service error",
        "summary",
        "impact",
        "db timeout",
        "detection",
        "resolution",
        "prevention",
        "# md",
        "{}",
        "alice",
        OffsetDateTime.now(),
        null,
        null,
        null,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }

  private PostmortemSectionRecord section(String id, String type, String title, String content) {
    return new PostmortemSectionRecord(
        id, "tenant_1", "pmr_1", 1, type, title, content, "{}", OffsetDateTime.now());
  }

  private PostmortemActionItemRecord actionItem() {
    return new PostmortemActionItemRecord(
        "pmai_1",
        "tenant_1",
        "pmr_1",
        "Update runbook",
        "Add rollback step",
        "bob",
        "high",
        "open",
        null,
        "manual",
        null,
        "alice",
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
