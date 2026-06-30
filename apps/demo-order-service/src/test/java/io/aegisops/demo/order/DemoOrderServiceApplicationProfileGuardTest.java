package io.aegisops.demo.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Guards ADR 0002: the profile gate in {@link DemoOrderServiceApplication#containsProfile} must
 * recognise the {@code demo} profile (case-insensitive) and reject empty/null profile lists. The
 * {@code main} method itself calls {@code System.exit} on failure, which is why this test only
 * exercises the pure helper.
 */
class DemoOrderServiceApplicationProfileGuardTest {

  @Test
  void shouldMatchDemoProfile() {
    assertThat(DemoOrderServiceApplication.containsProfile(new String[] {"demo"}, "demo")).isTrue();
  }

  @Test
  void shouldMatchDemoProfileCaseInsensitive() {
    assertThat(DemoOrderServiceApplication.containsProfile(new String[] {"DEMO"}, "demo")).isTrue();
  }

  @Test
  void shouldMatchAmongMultipleProfiles() {
    assertThat(
            DemoOrderServiceApplication.containsProfile(
                new String[] {"default", "demo", "metrics"}, "demo"))
        .isTrue();
  }

  @Test
  void shouldRejectEmptyProfileList() {
    assertThat(DemoOrderServiceApplication.containsProfile(new String[0], "demo")).isFalse();
  }

  @Test
  void shouldRejectNullProfileList() {
    assertThat(DemoOrderServiceApplication.containsProfile(null, "demo")).isFalse();
  }

  @Test
  void shouldRejectWhenDemoProfileAbsent() {
    assertThat(
            DemoOrderServiceApplication.containsProfile(new String[] {"prod", "metrics"}, "demo"))
        .isFalse();
  }
}
