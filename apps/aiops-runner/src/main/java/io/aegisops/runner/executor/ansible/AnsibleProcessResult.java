package io.aegisops.runner.executor.ansible;

public record AnsibleProcessResult(
    int exitCode, boolean timedOut, long durationMillis, String stdout, String stderr) {
  public boolean success() {
    return !timedOut && exitCode == 0;
  }
}
