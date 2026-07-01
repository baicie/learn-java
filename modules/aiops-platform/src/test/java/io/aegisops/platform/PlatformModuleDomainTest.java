package io.aegisops.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlatformModuleDomainTest {
  @Test
  void markHealthy_shouldReturnHealthyStatus() {
    PlatformModule module = new PlatformModule("1", "platform", "平台底座", "1.0.0", true, "UNKNOWN", "{}", null);

    PlatformModule healthy = module.markHealthy();

    assertThat(healthy.healthStatus()).isEqualTo("HEALTHY");
  }

  @Test
  void markUnhealthy_shouldReturnUnhealthyStatus() {
    PlatformModule module = new PlatformModule("1", "platform", "平台底座", "1.0.0", true, "UNKNOWN", "{}", null);

    PlatformModule unhealthy = module.markUnhealthy();

    assertThat(unhealthy.healthStatus()).isEqualTo("UNHEALTHY");
  }

  @Test
  void enabledModule_shouldBeEnabled() {
    PlatformModule module = new PlatformModule("1", "platform", "平台底座", "1.0.0", true, "HEALTHY", "{}", null);

    assertThat(module.enabled()).isTrue();
  }

  @Test
  void disabledModule_shouldNotBeEnabled() {
    PlatformModule module = new PlatformModule("1", "platform", "平台底座", "1.0.0", false, "HEALTHY", "{}", null);

    assertThat(module.enabled()).isFalse();
  }
}
