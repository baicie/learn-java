package io.aegisops.rca;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Indexes the diagnosis-evidence list for fast lookups by {@code evidenceType} and exposes keyword
 * search across title / summary / payload. Used by evidence-aware RCA rules to express "match if I
 * have X and Y".
 */
public final class RcaEvidenceIndex {
  private final List<RcaDiagnosisEvidenceRecord> evidence;
  private final Map<String, List<RcaDiagnosisEvidenceRecord>> byType;

  private RcaEvidenceIndex(List<RcaDiagnosisEvidenceRecord> evidence) {
    this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    this.byType =
        this.evidence.stream()
            .collect(Collectors.groupingBy(item -> normalize(item.evidenceType())));
  }

  public static RcaEvidenceIndex from(List<RcaDiagnosisEvidenceRecord> evidence) {
    return new RcaEvidenceIndex(evidence);
  }

  public boolean has(String evidenceType) {
    return !get(evidenceType).isEmpty();
  }

  public boolean hasAny(String... evidenceTypes) {
    if (evidenceTypes == null) {
      return false;
    }

    for (String type : evidenceTypes) {
      if (has(type)) {
        return true;
      }
    }

    return false;
  }

  public List<RcaDiagnosisEvidenceRecord> get(String evidenceType) {
    return byType.getOrDefault(normalize(evidenceType), List.of());
  }

  public List<RcaDiagnosisEvidenceRecord> getAny(String... evidenceTypes) {
    List<RcaDiagnosisEvidenceRecord> out = new ArrayList<>();

    if (evidenceTypes == null) {
      return out;
    }

    for (String type : evidenceTypes) {
      out.addAll(get(type));
    }

    return List.copyOf(out);
  }

  public List<RcaDiagnosisEvidenceRecord> all() {
    return evidence;
  }

  public List<String> refs(String... evidenceTypes) {
    return getAny(evidenceTypes).stream()
        .map(record -> record.evidenceKey())
        .filter(value -> value != null && !value.isBlank())
        .distinct()
        .toList();
  }

  public boolean containsText(String evidenceType, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return false;
    }

    String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
    return get(evidenceType).stream().anyMatch(contains(normalizedKeyword));
  }

  private static Predicate<RcaDiagnosisEvidenceRecord> contains(String keyword) {
    return evidence ->
        normalizeText(evidence.title()).contains(keyword)
            || normalizeText(evidence.summary()).contains(keyword)
            || normalizeText(evidence.payloadJson()).contains(keyword);
  }

  private static String normalizeText(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
