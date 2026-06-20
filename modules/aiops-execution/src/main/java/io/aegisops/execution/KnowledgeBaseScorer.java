package io.aegisops.execution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBaseScorer {
  public double cosine(List<Double> left, List<Double> right) {
    if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
      return 0.0d;
    }

    int size = Math.min(left.size(), right.size());
    double dot = 0.0d;
    double leftNorm = 0.0d;
    double rightNorm = 0.0d;

    for (int i = 0; i < size; i++) {
      double l = left.get(i) == null ? 0.0d : left.get(i);
      double r = right.get(i) == null ? 0.0d : right.get(i);
      dot += l * r;
      leftNorm += l * l;
      rightNorm += r * r;
    }

    if (leftNorm <= 0.0d || rightNorm <= 0.0d) {
      return 0.0d;
    }

    return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
  }

  public double keywordScore(String query, String content) {
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

  public double hybridScore(double vectorScore, double keywordScore) {
    return vectorScore * 0.8d + keywordScore * 0.2d;
  }

  private Set<String> tokens(String text) {
    if (text == null || text.isBlank()) {
      return Set.of();
    }

    String[] parts =
        text.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", " ").trim().split("\\s+");

    Set<String> tokens = new HashSet<>();
    for (String part : parts) {
      if (!part.isBlank()) {
        tokens.add(part);
      }
    }
    return tokens;
  }
}
