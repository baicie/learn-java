package io.aegisops.common.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Marks a Spring bean (or {@code @Bean} method) so that it is only registered when the runtime MVP
 * phase is at or above {@link #value()}.
 *
 * <p>Implements the Phase Discipline described in {@code .agents/skills/aegisops/SKILL.md §21}:
 * services belonging to a later MVP phase must not be wired into the application context when the
 * operator has not yet opted into that phase. The default {@code aiops.runtime.phase=phase0}
 * therefore excludes every phase-gated bean by default, so a misconfigured boot fails fast with a
 * missing bean instead of silently executing a later phase's logic.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Component
 * @PhaseEnabled(RuntimePhase.PHASE_5)
 * public class AnsibleRunnerService { ... }
 * }</pre>
 *
 * <p>Under the hood this delegates to {@link PhaseEnabledCondition}, which reads {@code
 * aiops.runtime.phase} via the Spring {@link org.springframework.core.env.Environment} and compares
 * it to the annotation's {@link #value()}. The condition intentionally reads the environment
 * directly so it does not require a fully constructed {@link RuntimeProperties} bean during
 * condition evaluation (which would create an ordering cycle).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(PhaseEnabledCondition.class)
public @interface PhaseEnabled {
  /**
   * The lowest MVP phase at which the annotated bean should be registered. A bean marked with
   * {@code @PhaseEnabled(PHASE_5)} will be active when the runtime phase is {@code phase5} or
   * {@code phase6} (i.e. "at or above" the given phase).
   */
  RuntimePhase value();
}
