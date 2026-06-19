package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import io.aegisops.execution.dto.PostmortemSourceBundle.AiDiagnosisSnapshot;
import io.aegisops.execution.dto.PostmortemSourceBundle.RcaSnapshot;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostmortemDraftBuilder {
  public PostmortemDraft build(PostmortemSourceBundle source, boolean generateActionItems) {
    String title = "Postmortem - " + source.incident().title();

    String rootCause = chooseRootCause(source);
    String summary =
        "Incident "
            + source.incident().id()
            + " was handled with status "
            + source.incident().status()
            + ". Severity="
            + source.incident().severity()
            + ".";

    String impact =
        switch (safe(source.incident().severity())) {
          case "critical" -> "Critical impact. Service availability or core business flow may have been affected.";
          case "high" -> "High impact. Users or important business functions may have been affected.";
          case "medium" -> "Medium impact. Partial degradation or limited scope impact was observed.";
          case "low" -> "Low impact. The incident appears to have limited customer-facing impact.";
          default -> "Impact requires manual confirmation.";
        };

    String detection =
        source.rcaAnalyses().isEmpty() && source.aiDiagnoses().isEmpty()
            ? "Detected by alert aggregation and incident creation."
            : "Detected by alert aggregation, followed by RCA and AI diagnosis.";

    String resolution = chooseResolution(source);
    String prevention =
        "Review monitoring coverage, runbook accuracy, rollback readiness, and ownership of follow-up action items.";

    List<String> actionItems = generateActionItems ? buildActionItems(source, rootCause) : List.of();

    return new PostmortemDraft(
        title, summary, impact, rootCause, detection, resolution, prevention, actionItems);
  }

  private String chooseRootCause(PostmortemSourceBundle source) {
    return source.aiDiagnoses().stream()
        .map(AiDiagnosisSnapshot::rootCause)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElseGet(
            () ->
                source.rcaAnalyses().stream()
                    .map(RcaSnapshot::rootCause)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("Root cause is not confirmed."));
  }

  private String chooseResolution(PostmortemSourceBundle source) {
    boolean hasSucceededExecution =
        source.executions().stream().anyMatch(item -> "succeeded".equals(item.status()));

    boolean hasRollback =
        source.rollbackPlans().stream()
            .anyMatch(item -> List.of("succeeded", "approved", "executing").contains(item.status()));

    if (hasRollback) {
      return "Rollback plan was created or executed as part of mitigation.";
    }

    if (hasSucceededExecution) {
      return "Automation execution succeeded as part of incident mitigation.";
    }

    return "Resolution requires manual confirmation.";
  }

  private List<String> buildActionItems(PostmortemSourceBundle source, String rootCause) {
    List<String> items = new ArrayList<>();

    items.add("Confirm the final root cause and attach supporting evidence.");
    items.add("Review and update the related runbook based on this incident.");
    items.add("Add or improve alerting rules to reduce detection time.");

    if (rootCause.toLowerCase().contains("unknown")) {
      items.add("Improve evidence collection for similar incidents.");
    }

    if (source.rollbackPlans().isEmpty()) {
      items.add("Prepare rollback plan for this failure mode.");
    }

    if (source.aiDiagnoses().isEmpty()) {
      items.add("Add this incident to AI diagnosis evaluation cases after review.");
    }

    return items;
  }

  private String safe(String value) {
    return value == null ? "" : value.toLowerCase();
  }
}
