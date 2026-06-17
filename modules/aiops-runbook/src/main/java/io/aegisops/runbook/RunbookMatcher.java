package io.aegisops.runbook;

import io.aegisops.runbook.dto.AiDiagnosisForPlanRecord;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RcaForPlanRecord;
import io.aegisops.runbook.dto.RunbookMatchResult;
import io.aegisops.runbook.dto.RunbookRecord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Deterministic runbook matcher. Scores by keyword, severity, category, fingerprint. */
public class RunbookMatcher {
  private static final int KEYWORD_SCORE = 20;
  private static final int SEVERITY_SCORE = 15;
  private static final int CATEGORY_SCORE = 10;
  private static final int FINGERPRINT_SCORE = 30;
  private static final int MAX_SCORE = 100;

  private final RunbookJson json;

  public RunbookMatcher(RunbookJson json) {
    this.json = json;
  }

  public List<RunbookMatchResult> match(
      IncidentForPlanRecord incident,
      List<AlertForPlanRecord> alerts,
      AiDiagnosisForPlanRecord diagnosis,
      RcaForPlanRecord rca,
      List<RunbookRecord> runbooks) {
    String evidenceText = RunbookText.evidenceText(incident, alerts, diagnosis, rca);

    return runbooks.stream()
        .filter(RunbookRecord::enabled)
        .map(runbook -> score(runbook, incident, alerts, evidenceText))
        .filter(result -> result.score() > 0)
        .sorted(Comparator.comparing(RunbookMatchResult::score).reversed())
        .toList();
  }

  public RunbookMatchResult score(
      RunbookRecord runbook,
      IncidentForPlanRecord incident,
      List<AlertForPlanRecord> alerts,
      String evidenceText) {
    var matchers = json.readMap(runbook.matchersJson());

    int score = 0;
    List<String> reasons = new ArrayList<>();

    score += scoreKeywords(matchers, evidenceText, reasons);
    score += scoreSeverity(matchers, incident.severity(), reasons);
    score += scoreCategory(matchers, runbook.category(), reasons);
    score += scoreFingerprints(matchers, alerts, reasons);

    if (score > MAX_SCORE) {
      score = MAX_SCORE;
    }

    BigDecimal confidence =
        BigDecimal.valueOf(score).divide(BigDecimal.valueOf(MAX_SCORE), 4, RoundingMode.HALF_UP);

    return new RunbookMatchResult(runbook, score, confidence, List.copyOf(reasons));
  }

  private int scoreKeywords(
      java.util.Map<String, Object> matchers, String evidenceText, List<String> reasons) {
    List<String> keywords = json.readStringList(matchers.get("keywords"));
    int score = 0;
    for (String keyword : keywords) {
      String normalized = keyword.toLowerCase(Locale.ROOT);
      if (!normalized.isBlank() && evidenceText.contains(normalized)) {
        score += KEYWORD_SCORE;
        reasons.add("keyword:" + keyword);
      }
    }
    return score;
  }

  private int scoreSeverity(
      java.util.Map<String, Object> matchers, String severity, List<String> reasons) {
    List<String> severities = json.readStringList(matchers.get("severities"));
    if (containsIgnoreCase(severities, severity)) {
      reasons.add("severity:" + severity);
      return SEVERITY_SCORE;
    }
    return 0;
  }

  private int scoreCategory(
      java.util.Map<String, Object> matchers, String category, List<String> reasons) {
    List<String> categories = json.readStringList(matchers.get("categories"));
    if (containsIgnoreCase(categories, category)) {
      reasons.add("category:" + category);
      return CATEGORY_SCORE;
    }
    return 0;
  }

  private int scoreFingerprints(
      java.util.Map<String, Object> matchers,
      List<AlertForPlanRecord> alerts,
      List<String> reasons) {
    List<String> fingerprints = json.readStringList(matchers.get("fingerprints"));
    int score = 0;
    for (AlertForPlanRecord alert : alerts) {
      if (containsIgnoreCase(fingerprints, alert.fingerprint())) {
        score += FINGERPRINT_SCORE;
        reasons.add("fingerprint:" + alert.fingerprint());
      }
    }
    return score;
  }

  private boolean containsIgnoreCase(List<String> values, String expected) {
    if (expected == null || expected.isBlank()) {
      return false;
    }

    String normalized = expected.toLowerCase(Locale.ROOT);
    return values.stream()
        .filter(value -> value != null)
        .map(value -> value.toLowerCase(Locale.ROOT))
        .anyMatch(normalized::equals);
  }
}
