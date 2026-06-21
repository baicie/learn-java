package io.aegisops.execution;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AgentMemoryScorer {
  public double score(String query, String title, String content, double confidence) {
    double keyword = keywordScore(query, title + "\n" + content);
    return round(keyword * 0.75d + confidence * 0.25d);
  }

  private double keywordScore(String query, String content) {
    Set<String> queryTokens = tokens(query);
    Set<String> contentTokens = tokens(content);

    if (queryTokens.isEmpty() || contentTokens.isEmpty()) {
      return 0.0d;
    }

    int hit = 0;
    for (String token : queryTokens) {
      if (contentTokens.contains(token)) {
        hit++;
      }
    }

    return (double) hit / (double) queryTokens.size();
  }

  private Set<String> tokens(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }

    String[] parts =
        text.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", " ").trim().split("\\s+");

    Set<String> result = new HashSet<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        result.add(part);
      }
    }
    return result;
  }

  private double round(double value) {
    return Math.round(value * 10000.0d) / 10000.0d;
  }
}
