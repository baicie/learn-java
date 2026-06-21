package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.security.SecurityConfig;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;

class SecurityFiltersConfigurationTest {
  @Test
  void registersSecurityFiltersAsSpringBean() {
    boolean registered =
        Arrays.stream(SecurityConfig.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Bean.class))
            .anyMatch(
                method -> method.getReturnType().equals(SecurityConfig.SecurityFilters.class));

    assertThat(registered).isTrue();
  }
}
