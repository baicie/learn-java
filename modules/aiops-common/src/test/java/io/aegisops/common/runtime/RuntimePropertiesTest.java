package io.aegisops.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimePropertiesTest {

  @Test
  void nullPhaseDefaultsToPhase0() {
    RuntimeProperties properties = new RuntimeProperties(null);
    assertThat(properties.phase()).isEqualTo(RuntimePhase.PHASE_0);
  }

  @Test
  void explicitPhasePreserved() {
    RuntimeProperties properties = new RuntimeProperties(RuntimePhase.PHASE_5);
    assertThat(properties.phase()).isEqualTo(RuntimePhase.PHASE_5);
  }

  @Test
  void defaultPhaseAccessorReturnsPhase0() {
    assertThat(RuntimeProperties.defaultPhase()).isEqualTo(RuntimePhase.PHASE_0);
  }
}
