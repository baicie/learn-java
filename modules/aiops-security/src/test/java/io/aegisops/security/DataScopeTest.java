package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataScopeTest {

  @Test
  void allMustOverrideSelf() {
    assertThat(
            DataScope.max(
                DataScope.SELF,
                DataScope.ALL))
        .isEqualTo(DataScope.ALL);
  }

  @Test
  void nullDefaultsToSelf() {
    assertThat(DataScope.from(null))
        .isEqualTo(DataScope.SELF);
  }
}
