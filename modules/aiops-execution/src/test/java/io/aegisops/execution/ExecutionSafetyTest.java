package io.aegisops.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ExecutionSafetyTest {
  @Test
  void executionModuleDoesNotExecuteExternalCommands() throws Exception {
    String content = readMainSources(Path.of("src/main/java"));

    assertFalse(content.contains("ProcessBuilder"));
    assertFalse(content.contains("Runtime.getRuntime"));
    assertFalse(content.contains("JSch"));
    assertFalse(content.contains("sshj"));
    assertFalse(content.contains("Ansible"));
    assertFalse(content.contains("RestTemplate"));
    assertFalse(content.contains("WebClient.create"));

    assertTrue(content.contains("lease"));
    assertTrue(content.contains("artifact"));
  }

  private String readMainSources(Path root) throws Exception {
    StringBuilder builder = new StringBuilder();
    try (var paths = Files.walk(root)) {
      paths
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  builder.append(Files.readString(path)).append('\n');
                } catch (Exception ex) {
                  throw new IllegalStateException(ex);
                }
              });
    }
    return builder.toString();
  }
}
