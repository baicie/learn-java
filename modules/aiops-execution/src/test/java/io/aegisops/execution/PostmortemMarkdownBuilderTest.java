package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aegisops.execution.dto.PostmortemContent;
import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostmortemMarkdownBuilderTest {
  @Test
  void buildMarkdown() {
    PostmortemMarkdownBuilder builder = new PostmortemMarkdownBuilder();

    String markdown =
        builder.build(
            new PostmortemContent(
                source(),
                "summary",
                "impact",
                "root cause",
                "detection",
                "resolution",
                "prevention",
                List.of("fix monitor", "update runbook")));

    assertTrue(markdown.contains("# Postmortem Report"));
    assertTrue(markdown.contains("Order service error"));
    assertTrue(markdown.contains("root cause"));
    assertTrue(markdown.contains("fix monitor"));
  }

  private PostmortemSourceBundle source() {
    return new PostmortemSourceBundle(
        new PostmortemSourceBundle.IncidentSnapshot(
            "inc_1",
            "Order service error",
            "resolved",
            "high",
            OffsetDateTime.now(),
            OffsetDateTime.now()),
        List.of(
            new PostmortemSourceBundle.RcaSnapshot(
                "rca_1", "rca summary", "db timeout", "0.8", OffsetDateTime.now())),
        List.of(),
        List.of(
            new PostmortemSourceBundle.ExecutionSnapshot(
                "exec_1",
                "live",
                "normal",
                "succeeded",
                "restart service",
                OffsetDateTime.now(),
                OffsetDateTime.now())),
        List.of(),
        List.of(
            new PostmortemSourceBundle.TimelineSnapshot(
                "tl_1",
                "incident_created",
                "Incident created",
                "incident was created",
                OffsetDateTime.now())));
  }
}
