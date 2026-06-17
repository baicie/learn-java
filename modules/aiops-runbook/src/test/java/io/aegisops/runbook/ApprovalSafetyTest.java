package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApprovalSafetyTest {
  @Test
  void phase51DoesNotIntroduceExecutionAdapters() throws Exception {
    String content = readMainSources();

    assertFalse(content.contains("JSch"));
    assertFalse(content.contains("sshj"));
    assertFalse(content.contains("Ansible"));
    assertFalse(content.contains("ProcessBuilder"));
    assertFalse(content.contains("Runtime.getRuntime"));
    assertFalse(content.contains("WebClient.create"));
    assertFalse(content.contains("RestTemplate"));
  }

  @Test
  void approvalCodeOnlyMovesPlanToApprovedButDoesNotExecute() throws Exception {
    String content = readMainSources();

    assertTrue(content.contains("pending_approval"));
    assertTrue(content.contains("approved"));
    assertFalse(content.contains("executePlan"));
    assertFalse(content.contains("runPlan"));
    assertFalse(content.contains("dispatchExecution"));
  }

  private String readMainSources() throws Exception {
    StringBuilder builder = new StringBuilder();

    try (var paths = Files.walk(Path.of("src/main/java"))) {
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
