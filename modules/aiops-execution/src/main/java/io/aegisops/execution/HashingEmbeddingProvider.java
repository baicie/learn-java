package io.aegisops.execution;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {
  private static final int DIMENSION = 128;

  @Override
  public int dimension() {
    return DIMENSION;
  }

  @Override
  public List<Double> embed(String text) {
    double[] vector = new double[DIMENSION];

    for (String token : tokenize(text)) {
      int index = positiveHash(token) % DIMENSION;
      vector[index] += 1.0d;
    }

    normalize(vector);

    List<Double> result = new ArrayList<>(DIMENSION);
    for (double value : vector) {
      result.add(value);
    }
    return result;
  }

  private List<String> tokenize(String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }

    return List.of(
        text.toLowerCase().replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", " ").trim().split("\\s+"));
  }

  private int positiveHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      int hash = 0;
      for (int i = 0; i < 4; i++) {
        hash = (hash << 8) | (bytes[i] & 0xff);
      }
      return hash & 0x7fffffff;
    } catch (Exception ex) {
      return Math.abs(value.hashCode());
    }
  }

  private void normalize(double[] vector) {
    double sum = 0.0d;
    for (double value : vector) {
      sum += value * value;
    }

    if (sum <= 0.0d) {
      return;
    }

    double norm = Math.sqrt(sum);
    for (int i = 0; i < vector.length; i++) {
      vector[i] = vector[i] / norm;
    }
  }
}
