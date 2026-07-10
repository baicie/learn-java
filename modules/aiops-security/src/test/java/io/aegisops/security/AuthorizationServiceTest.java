package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthorizationServiceTest {
  private final AuthorizationRepository repository =
      Mockito.mock(
          AuthorizationRepository.class);

  private final AuthorizationService service =
      new AuthorizationService(repository);

  @Test
  void shouldResolveSnapshotByTenantAndUser() {
    AuthorizationSnapshot expected =
        new AuthorizationSnapshot(
            Set.of("record_admin"),
            Set.of(
                PermissionCodes
                    .WORK_RECORD_READ_ALL),
            Map.of(
                "work-record",
                DataScope.ALL));

    when(
            repository.findSnapshot(
                "t1",
                "u1"))
        .thenReturn(expected);

    assertThat(
            service.resolve("t1", "u1"))
        .isEqualTo(expected);
  }

  @Test
  void shouldAssignNormalUserAsDefaultRole() {
    service.assignDefaultRole(
        "t1",
        "u1",
        "admin");

    verify(repository)
        .assignRole(
            "t1",
            "u1",
            BuiltInRoleCodes.NORMAL_USER,
            "admin");
  }
}
