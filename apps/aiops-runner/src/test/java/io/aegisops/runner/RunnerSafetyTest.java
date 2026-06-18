package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RunnerSafetyTest {
  @Test
  void runnerDoesNotUseLiveExecutionLibrariesInPhase54() throws Exception {
    String content = readMainSources(Path.of("src/main/java"));

    assertFalse(content.contains("ProcessBuilder"));
    assertFalse(content.contains("Runtime.getRuntime"));
    assertFalse(content.contains("JSch"));
    assertFalse(content.contains("sshj"));
    assertFalse(content.contains("Ansible"));
    assertFalse(content.contains("WebClient.create"));
    assertFalse(content.contains("RestTemplate"));

    assertTrue(content.contains("heartbeat"));
    assertTrue(content.contains("timeout"));
    assertTrue(content.contains("webhook"));
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
