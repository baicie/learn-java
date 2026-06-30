package io.aegisops.common.runtime;

import org.springframework.stereotype.Component;

/**
 * Demonstration bean wired through {@link PhaseEnabled}.
 *
 * <p>This bean exists to prove the {@link PhaseEnabled} / {@link PhaseEnabledCondition} contract
 * works end-to-end inside a Spring application context. It carries no business logic and is
 * deliberately kept in {@code aiops-common} so it has no cross-module dependencies; the
 * accompanying {@link PhaseGateIntegrationTest} is the real regression check.
 *
 * <p>Future PRs that need to gate late-phase services (e.g. {@code AnsibleRunnerService} for Phase
 * 5) should follow this same shape:
 *
 * <pre>{@code
 * @Component
 * @PhaseEnabled(RuntimePhase.PHASE_5)
 * public class AnsibleRunnerService { ... }
 * }</pre>
 */
@Component
@PhaseEnabled(RuntimePhase.PHASE_5)
public class PhaseGateProbe {
  /** Reports the lowest phase at which this bean is allowed to be active. */
  public RuntimePhase requiredPhase() {
    return RuntimePhase.PHASE_5;
  }
}
