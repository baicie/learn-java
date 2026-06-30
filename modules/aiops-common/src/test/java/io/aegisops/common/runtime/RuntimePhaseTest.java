package io.aegisops.common.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RuntimePhaseTest {

  @Test
  void propertyNameIsLowercase() {
    assertThat(RuntimePhase.PHASE_0.propertyName()).isEqualTo("phase0");
    assertThat(RuntimePhase.PHASE_5.propertyName()).isEqualTo("phase5");
    assertThat(RuntimePhase.PHASE_6.propertyName()).isEqualTo("phase6");
  }

  @Test
  void fromPropertyNameAcceptsValidLabels() {
    assertThat(RuntimePhase.fromPropertyName("phase0")).isEqualTo(RuntimePhase.PHASE_0);
    assertThat(RuntimePhase.fromPropertyName("phase5")).isEqualTo(RuntimePhase.PHASE_5);
    assertThat(RuntimePhase.fromPropertyName("PHASE_6")).isEqualTo(RuntimePhase.PHASE_6);
  }

  @Test
  void fromPropertyNameDefaultsToPhase0OnBlank() {
    assertThat(RuntimePhase.fromPropertyName(null)).isEqualTo(RuntimePhase.PHASE_0);
    assertThat(RuntimePhase.fromPropertyName("")).isEqualTo(RuntimePhase.PHASE_0);
    assertThat(RuntimePhase.fromPropertyName("   ")).isEqualTo(RuntimePhase.PHASE_0);
  }

  @Test
  void fromPropertyNameRejectsUnknownLabel() {
    assertThatThrownBy(() -> RuntimePhase.fromPropertyName("phase7"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("phase7");
    assertThatThrownBy(() -> RuntimePhase.fromPropertyName("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown runtime phase");
  }
}
