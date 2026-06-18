package io.aegisops.runner.executor.ansible;

import io.aegisops.common.exception.AppException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProcessBuilderAnsibleProcessRunner implements AnsibleProcessRunner {
  private final AnsibleRunnerProperties properties;

  public ProcessBuilderAnsibleProcessRunner(AnsibleRunnerProperties properties) {
    this.properties = properties;
  }

  @Override
  public AnsibleProcessResult run(
      List<String> argv, Path workingDirectory, Duration timeout, boolean requireCheckMode) {
    long started = System.currentTimeMillis();

    try {
      validateArgv(argv, requireCheckMode);

      ProcessBuilder builder = new ProcessBuilder(argv);
      builder.directory(workingDirectory.toFile());
      builder.redirectErrorStream(false);

      Process process = builder.start();
      var executor = Executors.newFixedThreadPool(2);

      try {
        Future<String> stdoutFuture = executor.submit(() -> readLimited(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(() -> readLimited(process.getErrorStream()));

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

        if (!finished) {
          process.destroyForcibly();
          process.waitFor(2, TimeUnit.SECONDS);

          return new AnsibleProcessResult(
              -1,
              true,
              System.currentTimeMillis() - started,
              readFutureBestEffort(stdoutFuture),
              readFutureBestEffort(stderrFuture));
        }

        return new AnsibleProcessResult(
            process.exitValue(),
            false,
            System.currentTimeMillis() - started,
            readFutureBestEffort(stdoutFuture),
            readFutureBestEffort(stderrFuture));
      } finally {
        executor.shutdownNow();
      }
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new AppException("ANSIBLE_PROCESS_FAILED", "Failed to execute ansible-playbook");
    }
  }

  private void validateArgv(List<String> argv, boolean requireCheckMode) {
    if (argv == null || argv.isEmpty()) {
      throw new AppException("ANSIBLE_ARGV_EMPTY", "Ansible argv is empty");
    }

    if (argv.stream().anyMatch(item -> item == null || item.isBlank())) {
      throw new AppException("ANSIBLE_ARGV_INVALID", "Ansible argv contains blank item");
    }

    validateBinary(argv.get(0));

    if (requireCheckMode && !argv.contains("--check")) {
      throw new AppException(
          "ANSIBLE_CHECK_MODE_REQUIRED", "Ansible sandbox execution must use --check");
    }

    if (argv.stream().anyMatch(this::isShellBinary)) {
      throw new AppException("ANSIBLE_SHELL_BLOCKED", "Shell execution is not allowed");
    }
  }

  private void validateBinary(String binary) {
    String baseName = baseName(binary);

    if (!"ansible-playbook".equals(baseName) && !"ansible-playbook.exe".equals(baseName)) {
      throw new AppException(
          "ANSIBLE_BINARY_NOT_ALLOWED",
          "Only ansible-playbook binary is allowed for Ansible execution");
    }
  }

  private boolean isShellBinary(String value) {
    String baseName = baseName(value);

    return List.of(
            "sh",
            "bash",
            "zsh",
            "dash",
            "fish",
            "cmd",
            "cmd.exe",
            "powershell",
            "powershell.exe",
            "pwsh",
            "pwsh.exe")
        .contains(baseName);
  }

  private String baseName(String value) {
    String normalized = value.replace('\\', '/');
    int index = normalized.lastIndexOf('/');
    String name = index >= 0 ? normalized.substring(index + 1) : normalized;
    return name.toLowerCase(Locale.ROOT);
  }

  private String readLimited(InputStream input) throws Exception {
    byte[] bytes = input.readAllBytes();
    String text = new String(bytes, StandardCharsets.UTF_8);
    int max = properties.normalizedMaxOutputChars();
    return text.length() <= max ? text : text.substring(0, max);
  }

  private String readFutureBestEffort(Future<String> future) {
    try {
      return future.get(1, TimeUnit.SECONDS);
    } catch (Exception ex) {
      return "";
    }
  }
}
