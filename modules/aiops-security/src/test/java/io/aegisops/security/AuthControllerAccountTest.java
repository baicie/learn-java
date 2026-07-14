package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.audit.AuditService;
import io.aegisops.common.exception.AppException;
import io.aegisops.user.UserAccount;
import io.aegisops.user.UserRepository;
import io.aegisops.user.UserService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthControllerAccountTest {

  @Test
  void currentUserCanUpdateProfileAndChangeOwnPassword() {
    UserService users = mock(UserService.class);
    AuthController controller =
        new AuthController(
            users,
            mock(JwtTokenService.class),
            mock(AuditService.class),
            mock(UserPrincipalFactory.class));
    UserPrincipal principal =
        new UserPrincipal(
            new UserPrincipal.Identity("u1", "t1", "alice", "Alice"), Set.of(), Set.of(), Map.of());
    UserAccount updated =
        new UserAccount(
            "u1",
            "t1",
            "alice",
            "Alice Zhang",
            "alice@example.com",
            "hash",
            "active",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(users.updateProfile("u1", "Alice Zhang", "alice@example.com")).thenReturn(updated);

    AuthController.ProfileResponse profile =
        controller
            .updateProfile(
                principal,
                new AuthController.UpdateProfileRequest("Alice Zhang", "alice@example.com"))
            .data();
    controller.changePassword(
        principal, new AuthController.ChangePasswordRequest("old-password", "new-password"));

    assertThat(profile.displayName()).isEqualTo("Alice Zhang");
    verify(users).changePassword("u1", "old-password", "new-password");
  }

  @Test
  void passwordChangeValidatesCurrentPasswordBeforeWritingHash() {
    UserRepository repository = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    UserService service = new UserService(repository, encoder);
    UserAccount user =
        new UserAccount(
            "u1",
            "t1",
            "alice",
            "Alice",
            "alice@example.com",
            "old-hash",
            "active",
            OffsetDateTime.now(),
            OffsetDateTime.now());
    when(repository.findById("u1")).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(() -> service.changePassword("u1", "wrong", "new-password"))
        .isInstanceOf(AppException.class);

    when(encoder.matches("old-password", "old-hash")).thenReturn(true);
    when(encoder.encode("new-password")).thenReturn("new-hash");
    service.changePassword("u1", "old-password", "new-password");

    verify(repository).updatePassword("u1", "new-hash");
  }
}
