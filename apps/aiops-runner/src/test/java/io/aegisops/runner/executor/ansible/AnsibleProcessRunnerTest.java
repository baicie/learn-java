package io.aegisops.runner.executor.ansible;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.aegisops.common.exception.AppException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnsibleProcessRunnerTest {
  @TempDir Path tempDir;

  @Test
  void rejectArgvWithoutCheck() {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    ProcessBuilderAnsibleProcessRunner runner = new ProcessBuilderAnsibleProcessRunner(properties);

    assertThrows(
        AppException.class,
        () ->
            runner.run(
                List.of("ansible-playbook", "-i", "inventory.ini", "playbook.yml"),
                tempDir,
                Duration.ofSeconds(1)));
  }

  @Test
  void rejectShellArgv() {
    AnsibleRunnerProperties properties = new AnsibleRunnerProperties();
    ProcessBuilderAnsibleProcessRunner runner = new ProcessBuilderAnsibleProcessRunner(properties);

    assertThrows(
        AppException.class,
        () ->
            runner.run(
                List.of("sh", "-c", "ansible-playbook --check"), tempDir, Duration.ofSeconds(1)));
  }
}
