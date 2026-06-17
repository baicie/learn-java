package io.aegisops.rca;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RcaPersistenceArchitectureTest {
  @Test
  void mainSourcesDoNotImportAegisTablesOrUseJdbcRepositoryNames() throws IOException {
    List<Path> violations =
        Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(this::isViolation)
            .toList();

    assertTrue(
        violations.isEmpty(), () -> "aiops-rca persistence architecture violations: " + violations);
  }

  private boolean isViolation(Path path) {
    String fileName = path.getFileName().toString();

    if (fileName.startsWith("Jdbc") && fileName.endsWith("Repository.java")) {
      return true;
    }

    try {
      String content = Files.readString(path);
      return content.contains("io.aegisops.persistence.AegisTables")
          || content.contains("AegisTables.");
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
