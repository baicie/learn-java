package io.aegisops.execution.service;

/**
 * Application-level contract for cross-module rollback plan mutations.
 *
 * <p>Same boundary rationale as {@link ExecutionApplicationService}: callers like the runner must
 * never inject {@code RollbackRepository} directly. An ArchUnit guard in {@code apps/aiops-runner}
 * enforces that. Only the two transitions the runner actually triggers ({@link #markSucceeded
 * markSucceeded} and {@link #markFailed markFailed}) are exposed; the rest of the rollback
 * lifecycle is owned by the web-side {@code ApprovalService} and friends inside aiops-execution.
 */
public interface RollbackApplicationService {

  /** Persist the rolled-back plan as succeeded. */
  boolean markSucceeded(String tenantId, String rollbackPlanId);

  /** Persist the rolled-back plan as failed. */
  boolean markFailed(String tenantId, String rollbackPlanId);
}
