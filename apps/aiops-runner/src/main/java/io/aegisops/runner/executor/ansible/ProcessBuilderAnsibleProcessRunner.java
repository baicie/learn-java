package io.aegisops.runner.executor.ansible;

import io.aegisops.common.exception.AppException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessBuilderAnsibleProcessRunner implements AnsibleProcessRunner {
  private final AnsibleRunnerProperties properties;

  public ProcessBuilderAnsibleProcessRunner(AnsibleRunnerProperties properties) {
    this.properties = properties;
  }

  @Override
  public AnsibleProcessResult run(List<String> argv, Path workingDirectory, Duration timeout) {
    long started = System.currentTimeMillis();

    try {
      validateArgv(argv);

      ProcessBuilder builder = new ProcessBuilder(argv);
      builder.directory(workingDirectory.toFile());
      builder.redirectErrorStream(false);

      Process process = builder.start();
      var executor = Executors.newFixedThreadPool(2);
      try {
        var stdoutFuture = executor.submit(() -> readLimited(process.getInputStream()));
        var stderrFuture = executor.submit(() -> readLimited(process.getErrorStream()));

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

        if (!finished) {
          process.destroyForcibly();
          return new AnsibleProcessResult(
              -1,
              true,
              System.currentTimeMillis() - started,
              stdoutFuture.get(1, TimeUnit.SECONDS),
              stderrFuture.get(1, TimeUnit.SECONDS));
        }

        return new AnsibleProcessResult(
            process.exitValue(),
            false,
            System.currentTimeMillis() - started,
            stdoutFuture.get(1, TimeUnit.SECONDS),
            stderrFuture.get(1, TimeUnit.SECONDS));
      } finally {
        executor.shutdownNow();
      }
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("ANSIBLE_PROCESS_FAILED", "Failed to execute ansible-playbook check");
    }
  }

  private void validateArgv(List<String> argv) {
    if (argv == null || argv.isEmpty()) {
      throw new AppException("ANSIBLE_ARGV_EMPTY", "Ansible argv is empty");
    }

    if (argv.stream().anyMatch(item -> item == null || item.isBlank())) {
      throw new AppException("ANSIBLE_ARGV_INVALID", "Ansible argv contains blank item");
    }

    if (!argv.contains("--check")) {
      throw new AppException(
          "ANSIBLE_CHECK_MODE_REQUIRED", "Ansible sandbox execution must use --check");
    }

    if (argv.stream()
        .anyMatch(
            item -> "sh".equals(item) || "bash".equals(item) || "cmd".equalsIgnoreCase(item))) {
      throw new AppException("ANSIBLE_SHELL_BLOCKED", "Shell execution is not allowed");
    }
  }

  private String readLimited(InputStream input) throws Exception {
    byte[] bytes = input.readAllBytes();
    String text = new String(bytes, StandardCharsets.UTF_8);
    int max = properties.normalizedMaxOutputChars();
    return text.length() <= max ? text : text.substring(0, max);
  }
}
