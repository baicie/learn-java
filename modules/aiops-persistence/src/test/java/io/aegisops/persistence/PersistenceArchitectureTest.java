package io.aegisops.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersistenceArchitectureTest {
  @Test
  void aegisTablesTransitionLayerHasBeenRemoved() {
    assertFalse(
        Files.exists(Path.of("src/main/java/io/aegisops/persistence/AegisTables.java")),
        "AegisTables should be removed after generated Tables migration.");
  }
}
