package io.aegisops.execution;

import java.util.List;

public interface EmbeddingProvider {
  int dimension();

  List<Double> embed(String text);
}
