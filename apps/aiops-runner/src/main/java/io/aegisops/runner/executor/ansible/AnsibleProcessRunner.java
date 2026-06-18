package io.aegisops.runner.executor.ansible;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface AnsibleProcessRunner {
  AnsibleProcessResult run(List<String> argv, Path workingDirectory, Duration timeout);
}
