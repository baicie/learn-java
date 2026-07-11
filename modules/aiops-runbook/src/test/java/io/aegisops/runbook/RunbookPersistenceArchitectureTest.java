package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunbookPersistenceArchitectureTest {
  @Test
  void runbookModuleDoesNotUseJdbcTemplateOrAegisTables() throws IOException {
    List<Path> violations =
        Files.walk(Path.of("src/main/java"))
            .filter(path -> path.toString().endsWith(".java"))
            .filter(javaClass -> isViolation(javaClass))
            .toList();

    assertTrue(
        violations.isEmpty(), () -> "runbook persistence architecture violations: " + violations);
  }

  private boolean isViolation(Path path) {
    try {
      String content = Files.readString(path);
      return content.contains("JdbcTemplate")
          || content.contains("NamedParameterJdbcTemplate")
          || content.contains("io.aegisops.persistence.AegisTables")
          || content.contains("AegisTables.");
    } catch (IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
