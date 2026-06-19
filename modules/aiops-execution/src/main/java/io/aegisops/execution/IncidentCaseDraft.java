package io.aegisops.execution;

import java.util.List;

public record IncidentCaseDraft(
    String title,
    String summary,
    String rootCause,
    String resolution,
    String prevention,
    List<Symptom> symptoms,
    List<ResolutionStep> resolutionSteps,
    List<String> tags) {
  public record Symptom(String symptomType, String name, String description) {}

  public record ResolutionStep(
      String title, String description, String actionType, String sourceRefId) {}
}
