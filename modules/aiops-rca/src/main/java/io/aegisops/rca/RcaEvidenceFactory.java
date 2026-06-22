package io.aegisops.rca;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RcaEvidenceFactory {
  private RcaEvidenceFactory() {}

  public static RcaEvidence fromDiagnosisEvidence(
      String ruleId,
      String title,
      BigDecimal score,
      BigDecimal confidence,
      List<RcaDiagnosisEvidenceRecord> refs) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("evidenceRefs", evidenceRefs(refs));
    attributes.put("evidenceTypes", evidenceTypes(refs));
    attributes.put("source", "diagnosis_evidence");

    String description = summarize(refs);

    return new RcaEvidence(ruleId, title, description, score, confidence, attributes);
  }

  public static RcaEvidence synthetic(
      String ruleId,
      String title,
      BigDecimal score,
      BigDecimal confidence,
      Map<String, Object> attributes) {
    return new RcaEvidence(
        ruleId,
        title,
        null,
        score,
        confidence,
        attributes == null ? Map.of() : Map.copyOf(attributes));
  }

  public static List<String> evidenceRefs(List<RcaDiagnosisEvidenceRecord> refs) {
    if (refs == null) {
      return List.of();
    }
    return refs.stream()
        .map(RcaDiagnosisEvidenceRecord::evidenceKey)
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .toList();
  }

  public static List<String> evidenceTypes(List<RcaDiagnosisEvidenceRecord> refs) {
    if (refs == null) {
      return List.of();
    }
    return refs.stream()
        .map(RcaDiagnosisEvidenceRecord::evidenceType)
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .toList();
  }

  private static String summarize(List<RcaDiagnosisEvidenceRecord> refs) {
    if (refs == null || refs.isEmpty()) {
      return "No diagnosis evidence refs attached.";
    }
    return refs.stream()
        .map(RcaDiagnosisEvidenceRecord::summary)
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .limit(3)
        .reduce((left, right) -> left + "；" + right)
        .orElse("Matched diagnosis evidence refs: " + evidenceRefs(refs));
  }
}
