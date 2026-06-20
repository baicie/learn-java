package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashingEmbeddingProviderTest {
  private final HashingEmbeddingProvider provider = new HashingEmbeddingProvider();

  @Test
  void embedReturnsFixedDimension() {
    var embedding = provider.embed("redis timeout order service");

    assertEquals(provider.dimension(), embedding.size());
  }

  @Test
  void embedNormalizesVector() {
    var embedding = provider.embed("redis timeout order service");

    double norm = Math.sqrt(embedding.stream().mapToDouble(value -> value * value).sum());

    assertTrue(norm > 0.99d && norm < 1.01d);
  }

  @Test
  void sameTextProducesSameEmbedding() {
    assertEquals(provider.embed("same text"), provider.embed("same text"));
  }
}
