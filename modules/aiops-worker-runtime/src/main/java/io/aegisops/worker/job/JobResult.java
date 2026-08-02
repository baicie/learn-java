package io.aegisops.worker.job;

/**
 * Outcome of a single {@link OutboxJob#handle} call.
 *
 * <p>Poller uses this to decide between {@code markDone} (success) and {@code recordFailure} (retry
 * or give up). {@link #skip()} is reserved for permanent skips where retrying would not change the
 * outcome (e.g. unknown job name). MVP ships only success / failure.
 */
public record JobResult(Status status, String reason) {
  public enum Status {
    SUCCESS,
    FAILURE
  }

  public static JobResult success() {
    return new JobResult(Status.SUCCESS, null);
  }

  public static JobResult failure(String reason) {
    return new JobResult(Status.FAILURE, reason);
  }

  public boolean isSuccess() {
    return status == Status.SUCCESS;
  }
}
