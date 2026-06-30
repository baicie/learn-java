package io.aegisops.execution.service;

import io.aegisops.execution.RollbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default JOOQ-backed implementation of {@link RollbackApplicationService}.
 *
 * <p>Symmetric counterpart to {@link JooqExecutionApplicationService}. The runner only triggers the
 * two terminal transitions of a rollback plan (succeeded / failed); the rest of the lifecycle stays
 * inside aiops-execution and is owned by the web-side controllers and approval flow.
 */
@Service
public class JooqRollbackApplicationService implements RollbackApplicationService {

  private final RollbackRepository rollbackRepository;

  public JooqRollbackApplicationService(RollbackRepository rollbackRepository) {
    this.rollbackRepository = rollbackRepository;
  }

  @Override
  @Transactional
  public boolean markSucceeded(String tenantId, String rollbackPlanId) {
    return rollbackRepository.markSucceeded(tenantId, rollbackPlanId);
  }

  @Override
  @Transactional
  public boolean markFailed(String tenantId, String rollbackPlanId) {
    return rollbackRepository.markFailed(tenantId, rollbackPlanId);
  }
}
