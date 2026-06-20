package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class KnowledgeBaseScorerTest {
  private final KnowledgeBaseScorer scorer = new KnowledgeBaseScorer();

  @Test
  void cosineIdenticalVectorIsOne() {
    double score = scorer.cosine(List.of(1.0, 0.0, 0.0), List.of(1.0, 0.0, 0.0));

    assertEquals(1.0d, score, 0.0001d);
  }

  @Test
  void cosineOrthogonalVectorIsZero() {
    double score = scorer.cosine(List.of(1.0, 0.0), List.of(0.0, 1.0));

    assertEquals(0.0d, score, 0.0001d);
  }

  @Test
  void keywordScoreFindsOverlap() {
    double score =
        scorer.keywordScore("redis timeout", "order service redis connection timeout happened");

    assertTrue(score > 0.9d);
  }

  @Test
  void hybridScoreUsesBothSignals() {
    double score = scorer.hybridScore(0.8d, 0.5d);

    assertEquals(0.74d, score, 0.0001d);
  }
}
