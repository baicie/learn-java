package io.aegisops.workrecord.domain.rule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FieldCodeRulesTest {
  @Test
  void shouldRejectInvalidFieldCode() {
    assertThatThrownBy(() -> FieldCodeRules.validate("1bad"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid fieldCode");
  }

  @Test
  void shouldRejectReservedFieldCode() {
    assertThatThrownBy(() -> FieldCodeRules.validate("tenant_id"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reserved fieldCode");
  }

  @Test
  void shouldAcceptValidFieldCode() {
    FieldCodeRules.validate("priority_1");
  }
}
