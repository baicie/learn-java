package io.aegisops.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AgentEvalScorer {
  public AgentEvalScore score(AgentEvalScoringInputs inputs) {
    AgentEvalScoringInputs.AgentEvalExpected expected = inputs.expected();
    AgentEvalScoringInputs.AgentEvalActual actualFields = inputs.actual();

    String actual =
        join(actualFields.summary(), actualFields.rootCause(), actualFields.recommendation())
            .toLowerCase(Locale.ROOT);

    double rootCauseScore = containsAllImportantTerms(actual, expected.rootCause()) ? 1.0d : 0.0d;

    KeywordResult keywordResult = keywordScore(actual, expected.keywords());
    KeywordResult actionResult = keywordScore(actual, expected.actions());
    ForbiddenResult forbiddenResult = forbiddenScore(actual, expected.forbidden());

    double score =
        round(
            rootCauseScore * 0.45d
                + keywordResult.score() * 0.30d
                + actionResult.score() * 0.20d
                + forbiddenResult.score() * 0.05d);

    boolean passed = score >= 0.70d && forbiddenResult.score() >= 1.0d;

    return new AgentEvalScore(
        score,
        round(rootCauseScore),
        round(keywordResult.score()),
        round(actionResult.score()),
        round(forbiddenResult.score()),
        passed,
        keywordResult.matched(),
        keywordResult.missing(),
        forbiddenResult.hits());
  }

  private KeywordResult keywordScore(String actual, List<String> expected) {
    List<String> items = clean(expected);
    if (items.isEmpty()) {
      return new KeywordResult(1.0d, List.of(), List.of());
    }

    List<String> matched = new ArrayList<>();
    List<String> missing = new ArrayList<>();

    for (String item : items) {
      if (actual.contains(item.toLowerCase(Locale.ROOT))) {
        matched.add(item);
      } else {
        missing.add(item);
      }
    }

    return new KeywordResult((double) matched.size() / (double) items.size(), matched, missing);
  }

  private ForbiddenResult forbiddenScore(String actual, List<String> forbidden) {
    List<String> items = clean(forbidden);
    if (items.isEmpty()) {
      return new ForbiddenResult(1.0d, List.of());
    }

    List<String> hits = new ArrayList<>();
    for (String item : items) {
      if (actual.contains(item.toLowerCase(Locale.ROOT))) {
        hits.add(item);
      }
    }

    return new ForbiddenResult(hits.isEmpty() ? 1.0d : 0.0d, hits);
  }

  private boolean containsAllImportantTerms(String actual, String expectedRootCause) {
    if (expectedRootCause == null || expectedRootCause.isBlank()) {
      return true;
    }

    List<String> terms = clean(List.of(expectedRootCause.split("\\s+")));
    if (terms.isEmpty()) {
      return true;
    }

    int matched = 0;
    for (String term : terms) {
      if (actual.contains(term.toLowerCase(Locale.ROOT))) {
        matched++;
      }
    }

    return ((double) matched / (double) terms.size()) >= 0.5d;
  }

  private List<String> clean(List<String> values) {
    if (values == null) {
      return List.of();
    }

    return values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(text -> text.trim())
        .distinct()
        .toList();
  }

  private String join(String... parts) {
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part != null) {
        builder.append(part).append("\n");
      }
    }
    return builder.toString();
  }

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }

  private record KeywordResult(double score, List<String> matched, List<String> missing) {}

  private record ForbiddenResult(double score, List<String> hits) {}
}
