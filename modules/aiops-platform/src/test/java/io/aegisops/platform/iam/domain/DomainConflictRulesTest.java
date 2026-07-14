package io.aegisops.platform.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DomainConflictRulesTest {

  @Test
  void should_recognize_pending_status_as_buffer_state() {
    assertThat(PlatformUserStatus.from("pending")).isEqualTo(PlatformUserStatus.PENDING);
    assertThat(PlatformUserStatus.PENDING.value()).isEqualTo("pending");
  }

  @Test
  void should_keep_replace_command_carrying_role_codes_as_immutable_list() {
    ReplaceUserRolesCommand command =
        new ReplaceUserRolesCommand(List.of("platform-admin", "ops-viewer"), "manual review", 3);
    assertThat(command.roleCodes()).containsExactly("platform-admin", "ops-viewer");
    assertThat(command.rowVersion()).isEqualTo(3);
  }

  @Test
  void should_not_mutation_protect_create_userdata() {
    CreatePlatformUserData data =
        new CreatePlatformUserData(
            "alice", "Alice", "[email protected]", "changeMe-9!", "ACTIVE", java.util.Set.of());
    data.validate();
    assertThat(data.username()).isEqualTo("alice");
  }

  @Test
  void should_reject_short_initial_password() {
    CreatePlatformUserData data =
        new CreatePlatformUserData(
            "alice", "Alice", "[email protected]", "short", "ACTIVE", java.util.Set.of());
    org.assertj.core.api.Assertions.assertThatThrownBy(data::validate)
        .isInstanceOf(IllegalArgumentException.class);
  }
}
