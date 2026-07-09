package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.security.UserPrincipal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordPermissionServiceTest {
  private final WorkRecordPermissionService service = new WorkRecordPermissionService();

  @Test
  void shouldDetectReadAllPermission() {
    UserPrincipal user = new UserPrincipal("u1", "tenant1", "admin", "", Set.of("admin"));

    assertThat(service.canReadAll(user)).isTrue();
  }

  @Test
  void shouldRejectMissingPermission() {
    UserPrincipal user = new UserPrincipal("u1", "tenant1", "normal", "", Set.of("operator"));

    assertThat(service.canReadAll(user)).isFalse();
  }
}
