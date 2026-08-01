package io.aegisops.execution.service;

import io.aegisops.execution.ExecutionReportRepository;
import io.aegisops.execution.ExecutionRepository;
import io.aegisops.execution.dto.ExecutionArtifactCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionRunStatusUpdateCommand;
import io.aegisops.execution.dto.ExecutionStepRecord;
import io.aegisops.execution.dto.ExecutionStepStatusUpdateCommand;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default JOOQ-backed implementation of {@link ExecutionApplicationService}.
 *
 * <p>This class is the only place in the codebase that touches {@code ExecutionRepository}; every
 * cross-module caller goes through the interface, so the runner never imports a {@code *Repository}
 * type directly. The ArchUnit test in {@code apps/aiops-runner} enforces the rule.
 *
 * <p>Methods are {@code @Transactional} so the runner's compound operations (e.g. status update +
 * plan update + rollback mark) remain atomic even when the caller chain splits them across helpers.
 * We keep read-only ones inside the same transaction to avoid surprising the existing {@code
 * ExecutionRequestService} callers that may join the same transaction.
 */
@Service
public class JooqExecutionApplicationService implements ExecutionApplicationService {

  private final ExecutionRepository executionRepository;
  private final ExecutionReportRepository executionReportRepository;

  public JooqExecutionApplicationService(
      ExecutionRepository executionRepository,
      ExecutionReportRepository executionReportRepository) {
    this.executionRepository = executionRepository;
    this.executionReportRepository = executionReportRepository;
  }

  @Override
  @Transactional
  public Optional<ExecutionRunRecord> claimNextQueuedRun(
      String runnerId, OffsetDateTime now, OffsetDateTime leaseUntil) {
    return executionRepository.claimNextQueuedRun(runnerId, now, leaseUntil);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ExecutionStepRecord> listExecutionSteps(String tenantId, String executionId) {
    return executionRepository.listExecutionSteps(tenantId, executionId);
  }

  @Override
  @Transactional
  public boolean heartbeat(
      String tenantId,
      String executionId,
      String runnerId,
      OffsetDateTime heartbeatAt,
      OffsetDateTime leaseUntil) {
    return executionRepository.heartbeat(tenantId, executionId, runnerId, heartbeatAt, leaseUntil);
  }

  @Override
  @Transactional
  public boolean updateStepStatus(ExecutionStepStatusUpdateCommand command) {
    return executionRepository.updateStepStatus(command);
  }

  @Override
  @Transactional
  public void createArtifact(ExecutionArtifactCreateCommand command) {
    executionRepository.createArtifact(command);
  }

  @Override
  @Transactional
  public void appendAuditEvent(ExecutionAuditEventCreateCommand command) {
    executionReportRepository.createAuditEvent(command);
  }

  @Override
  @Transactional
  public boolean incrementStepArtifactCount(String tenantId, String stepId) {
    return executionRepository.incrementStepArtifactCount(tenantId, stepId);
  }

  @Override
  @Transactional
  public boolean updatePlanStatus(String tenantId, String planId, String status) {
    return executionRepository.updatePlanStatus(tenantId, planId, status);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ExecutionRunRecord> findExpiredRunningRuns(OffsetDateTime now, int limit) {
    return executionRepository.findExpiredRunningRuns(now, limit);
  }

  @Override
  @Transactional
  public boolean timeoutRun(String tenantId, String executionId, String errorMessage) {
    return executionRepository.timeoutRun(tenantId, executionId, errorMessage);
  }

  @Override
  @Transactional
  public boolean timeoutExecutionSteps(String tenantId, String executionId) {
    return executionRepository.timeoutExecutionSteps(tenantId, executionId);
  }

  @Override
  @Transactional
  public boolean updateRunStatus(ExecutionRunStatusUpdateCommand command) {
    return executionRepository.updateRunStatus(command);
  }

  @Override
  @Transactional
  public boolean markLiveGuardPassed(String tenantId, String executionId) {
    return executionRepository.markLiveGuardPassed(tenantId, executionId);
  }
}
