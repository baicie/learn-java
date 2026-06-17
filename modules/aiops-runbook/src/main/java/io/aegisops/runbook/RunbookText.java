package io.aegisops.runbook;

import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Plain-text rendering helpers for runbook matching and variable substitution. */
public final class RunbookText {
  private RunbookText() {}

  /** Concatenates all evidence fields into a single lower-cased text for keyword matching. */
  public static String evidenceText(
      IncidentForPlanRecord incident,
      List<AlertForPlanRecord> alerts,
      AiDiagnosisForPlanRecord diagnosis,
      RcaForPlanRecord rca) {
    StringBuilder builder = new StringBuilder();

    append(builder, incident.title());
    append(builder, incident.summary());
    append(builder, incident.severity());
    append(builder, incident.primaryAssetId());
    append(builder, incident.suspectedRootCause());

    for (AlertForPlanRecord alert : alerts) {
      append(builder, alert.title());
      append(builder, alert.description());
      append(builder, alert.severity());
      append(builder, alert.assetId());
      append(builder, alert.entityName());
      append(builder, alert.fingerprint());
      append(builder, alert.labelsJson());
    }

    if (diagnosis != null) {
      append(builder, diagnosis.summary());
      append(builder, diagnosis.rootCause());
      append(builder, diagnosis.impact());
      append(builder, diagnosis.nextStepsJson());
      append(builder, diagnosis.runbookSuggestionsJson());
      append(builder, diagnosis.risksJson());
    }

    if (rca != null) {
      append(builder, rca.summary());
      append(builder, rca.suspectedRootCause());
      append(builder, rca.evidenceJson());
    }

    return builder.toString().toLowerCase(Locale.ROOT);
  }

  /** Standard variables exposed to runbook step command templates. */
  public static Map<String, String> variables(IncidentForPlanRecord incident) {
    return Map.of(
        "incidentId", safe(incident.id()),
        "tenantId", safe(incident.tenantId()),
        "title", safe(incident.title()),
        "severity", safe(incident.severity()),
        "assetId", safe(incident.primaryAssetId()),
        "status", safe(incident.status()));
  }

  /**
   * Replace {@code {{key}}} placeholders in {@code template} with values from {@code variables}.
   * Unknown keys are left as-is.
   */
  public static String renderTemplate(String template, Map<String, String> variables) {
    if (template == null || template.isBlank()) {
      return "";
    }

    String rendered = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
    }
    return rendered;
  }

  private static void append(StringBuilder builder, String value) {
    if (value != null && !value.isBlank()) {
      builder.append(' ').append(value);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
