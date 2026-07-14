package io.aegisops.platform.iam;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.ReplaceRoleDataScopesCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReplaceRoleDataScopesCommandTest {

  @Test
  void should_drop_empty_resources() {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("regionIds", List.of("cn-north-1"));
    ReplaceRoleDataScopesCommand command =
        new ReplaceRoleDataScopesCommand(
            List.of(
                new PlatformRole.RoleDataScope("incident", "DEPARTMENT", detail),
                new PlatformRole.RoleDataScope("  ", "SELF", Map.of()),
                new PlatformRole.RoleDataScope(null, "ALL", Map.of())),
            1);
    List<PlatformRole.RoleDataScope> normalized = command.normalized();

    assertThat(normalized).hasSize(1);
    assertThat(normalized.get(0).resourceCode()).isEqualTo("incident");
    assertThat(normalized.get(0).scopeType()).isEqualTo("DEPARTMENT");
    assertThat(normalized.get(0).detail()).containsEntry("regionIds", List.of("cn-north-1"));
  }

  @Test
  void should_default_scope_type_to_self() {
    ReplaceRoleDataScopesCommand command =
        new ReplaceRoleDataScopesCommand(
            List.of(new PlatformRole.RoleDataScope("incident", null, Map.of())), 1);
    List<PlatformRole.RoleDataScope> scopes = command.normalized();

    assertThat(scopes).hasSize(1);
    assertThat(scopes.get(0).scopeType()).isEqualTo("SELF");
  }

  @Test
  void should_tolerate_null_input() {
    ReplaceRoleDataScopesCommand command = new ReplaceRoleDataScopesCommand(null, 0);
    assertThat(command.normalized()).isEmpty();
    // Re-evaluating normalized() must be safe and idempotent.
    assertThat(command.normalized()).isEmpty();
  }
}
