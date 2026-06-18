package io.aegisops.runner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RunnerSafetyTest {
  @Test
  void runnerUsesProcessBuilderOnlyInAnsibleProcessRunner() throws Exception {
    String content = readMainSources(Path.of("src/main/java"));

    assertFalse(content.contains("Runtime.getRuntime"));
    assertFalse(content.contains("JSch"));
    assertFalse(content.contains("sshj"));
    assertFalse(content.contains("AnsibleVault"));

    int processBuilderCount = count(content, "new ProcessBuilder");
    assertTrue(processBuilderCount <= 1);

    Path runnerFile =
        Path.of(
            "src/main/java/io/aegisops/runner/executor/ansible/ProcessBuilderAnsibleProcessRunner.java");
    assertTrue(Files.readString(runnerFile).contains("new ProcessBuilder(argv)"));
    assertFalse(Files.readString(runnerFile).contains("sh -c"));
    assertFalse(Files.readString(runnerFile).contains("cmd /c"));
    assertTrue(Files.readString(runnerFile).contains("--check"));
  }

  private int count(String content, String pattern) {
    int count = 0;
    int index = 0;
    while ((index = content.indexOf(pattern, index)) >= 0) {
      count++;
      index += pattern.length();
    }
    return count;
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
