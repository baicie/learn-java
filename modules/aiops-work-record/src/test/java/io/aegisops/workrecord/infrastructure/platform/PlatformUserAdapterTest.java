package io.aegisops.workrecord.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.NotFoundException;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserService;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PlatformUserAdapterTest {
  private final UserService userService = Mockito.mock(UserService.class);
  private final PlatformUserAdapter adapter = new PlatformUserAdapter(userService);

  @Test
  void shouldAcceptActiveUserInSameTenant() {
    when(userService.getById("u1")).thenReturn(user("u1", "t1", "active"));

    adapter.requireActiveUser("t1", "u1");
  }

  @Test
  void shouldRejectUserFromOtherTenant() {
    when(userService.getById("u1")).thenReturn(user("u1", "t2", "active"));

    assertThatThrownBy(() -> adapter.requireActiveUser("t1", "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user does not belong to tenant");
  }

  @Test
  void shouldRejectDisabledUser() {
    when(userService.getById("u1")).thenReturn(user("u1", "t1", "disabled"));

    assertThatThrownBy(() -> adapter.requireActiveUser("t1", "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user is not active");
  }

  @Test
  void shouldTranslateUserNotFound() {
    when(userService.getById("missing"))
        .thenThrow(new NotFoundException("User not found: missing"));

    assertThatThrownBy(() -> adapter.requireActiveUser("t1", "missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("user not found: missing");
  }

  @Test
  void shouldRejectBlankUserId() {
    assertThatThrownBy(() -> adapter.requireActiveUser("t1", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("userId is required");
  }

  private UserAccount user(String id, String tenantId, String status) {
    return new UserAccount(
        id,
        tenantId,
        "alice",
        "Alice",
        "alice@example.com",
        "hash",
        status,
        Set.of("operator"),
        OffsetDateTime.now(),
        OffsetDateTime.now());
  }
}