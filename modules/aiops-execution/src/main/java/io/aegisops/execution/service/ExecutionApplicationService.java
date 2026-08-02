package io.aegisops.execution.service;

import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Application-level contract that wraps the internal {@code ExecutionRepository} for cross-module
 * consumers such as the runner.
 *
 * <p>Only the methods that {@code RunnerExecutionService} genuinely needs are exposed here, so the
 * platform boundary stays narrow and the underlying JOOQ types never leak across module edges.
 * Callers MUST go through this interface instead of injecting {@code ExecutionRepository} directly;
 * an ArchUnit guard in {@code apps/aiops-runner} enforces that.
 *
 * <p>All implementations must be idempotent-friendly: the underlying {@code ExecutionRepository}
 * returns a {@code boolean} indicating whether the row update actually happened. Callers treat a
 * {@code false} result as a hard error and translate it into an {@code AppException}.
 */
public interface ExecutionApplicationService {

  /**
   * Claim the next queued run that is due for this runner.
   *
   * @param runnerId the runner instance claiming the work
   * @param now the current wall-clock timestamp
   * @param leaseUntil the moment the lease (and therefore the {@code claimed_by} lock) expires
   */
  Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil);

  /** Return the steps belonging to {@code executionId} in declaration order. */
  List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId);

  /**
   * Refresh the lease for {@code executionId}.
   *
   * @return {@code true} when the heartbeat was persisted; callers must surface {@code false} as an
   *     error
   */
  boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil);

  /** Persist a step status transition; returns {@code true} when the row was updated. */
  boolean updateStepStatus(ExecutionStepStatusUpdateCommand command);

  /** Persist a new artifact row; matches the legacy {@code ExecutionRepository} contract. */
  void createArtifact(ExecutionArtifactCreateCommand command);

  /** Append a runner audit event without exposing execution repositories across the module edge. */
  void appendAuditEvent(ExecutionAuditEventCreateCommand command);

  /** Increment {@code artifact_count} on the step row; {@code false} indicates no row matched. */
  boolean incrementStepArtifactCount(String tenantId, String stepId);

  /** Drive the automation plan row to the requested state. */
  boolean updatePlanStatus(String tenantId, String planId, String status);

  /** Find runs whose lease has already expired and are still marked {@code running}. */
  List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit);

  /** Mark {@code executionId} as timed out with {@code errorMessage}. */
  boolean timeoutRun(String tenantId, String executionId, String errorMessage);

  /** Mark every still-running step under {@code executionId} as timed out. */
  boolean timeoutExecutionSteps(String tenantId, String executionId);

  /** Persist a final status (succeeded / failed) for the run. */
  boolean updateRunStatus(ExecutionRunStatusUpdateCommand command);

  /**
   * Record that the live-guard pre-execution check for {@code executionId} has passed.
   *
   * <p>Used by the {@code AnsibleStepExecutor} after confirming the target host is reachable before
   * issuing a live execution command.
   */
  boolean markLiveGuardPassed(String tenantId, String executionId);
}
