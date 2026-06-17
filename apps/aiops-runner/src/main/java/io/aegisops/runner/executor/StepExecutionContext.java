package io.aegisops.runner.executor;

import io.aegisops.execution.dto.ExecutionRunRecord;

public record StepExecutionContext(ExecutionRunRecord run, boolean liveEnabled) {
  public boolean dryRun() {
    return "dry_run".equals(run.mode());
  }
}
