package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostmortemDraftBuilderTest {
  private final PostmortemDraftBuilder builder = new PostmortemDraftBuilder();

  @Test
  void chooseAiRootCauseFirst() {
    PostmortemDraft draft =
        builder.build(
            new PostmortemSourceBundle(
                incident("high"),
                List.of(
                    new PostmortemSourceBundle.RcaSnapshot(
                        "rca_1", "rca summary", "rca root cause", "0.8", OffsetDateTime.now())),
                List.of(
                    new PostmortemSourceBundle.AiDiagnosisSnapshot(
                        "ai_1",
                        "ai summary",
                        "ai root cause",
                        "restart service",
                        OffsetDateTime.now())),
                List.of(),
                List.of(),
                List.of()),
            true);

    assertEquals("ai root cause", draft.rootCause());
    assertTrue(
        draft
            .actionItems()
            .contains("Review and update the related runbook based on this incident."));
  }

  @Test
  void generateRollbackActionItemWhenNoRollbackPlan() {
    PostmortemDraft draft =
        builder.build(
            new PostmortemSourceBundle(
                incident("medium"), List.of(), List.of(), List.of(), List.of(), List.of()),
            true);

    assertTrue(draft.actionItems().contains("Prepare rollback plan for this failure mode."));
  }

  private PostmortemSourceBundle.IncidentSnapshot incident(String severity) {
    return new PostmortemSourceBundle.IncidentSnapshot(
        "inc_1",
        "Order service error",
        "resolved",
        severity,
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}
