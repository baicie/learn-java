package io.aegisops.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * End-to-end test for the {@link PhaseEnabled} gate.
 *
 * <p>Loads a minimal Spring context through {@link ApplicationContextRunner} and asserts that
 * {@link PhaseGateProbe} is registered when the runtime phase is at or above {@link
 * RuntimePhase#PHASE_5} and absent otherwise. This is the regression test that {@link
 * PhaseEnabledConditionTest} cannot provide: it verifies the full annotation, condition and Spring
 * container wiring instead of the condition in isolation.
 */
class PhaseGateIntegrationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PhaseGateProbe.class);

  @Test
  void probeIsRegisteredWhenRuntimePhaseIsAtOrAbovePhase5() {
    runner
        .withPropertyValues("aiops.runtime.phase=phase5")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(PhaseGateProbe.class);
              assertThat(context.getBean(PhaseGateProbe.class).requiredPhase())
                  .isEqualTo(RuntimePhase.PHASE_5);
            });
  }

  @Test
  void probeIsRegisteredAtPhase6() {
    runner
        .withPropertyValues("aiops.runtime.phase=phase6")
        .run(context -> assertThat(context).hasSingleBean(PhaseGateProbe.class));
  }

  @Test
  void probeIsAbsentWhenRuntimePhaseIsBelowPhase5() {
    runner
        .withPropertyValues("aiops.runtime.phase=phase4")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(PhaseGateProbe.class);
            });
  }

  @Test
  void probeIsAbsentByDefaultWhenPhasePropertyIsMissing() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(PhaseGateProbe.class);
        });
  }

  @Test
  void probeIsAbsentWhenPhaseValueIsUnrecognised() {
    runner
        .withPropertyValues("aiops.runtime.phase=phase-typo")
        .run(
            context ->
                assertThat(context)
                    .as("unknown phase values must not silently enable the bean")
                    .doesNotHaveBean(PhaseGateProbe.class));
  }
}
