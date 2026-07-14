package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PermissionCodesTest {

  @Test
  void phase13PermissionCodesMustBeUniqueAndComplete() {
    Set<String> reflected =
        Arrays.stream(PermissionCodes.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> field.getType().equals(String.class))
            .map(
                field -> {
                  try {
                    return (String) field.get(null);
                  } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                  }
                })
            .collect(Collectors.toSet());

    assertThat(reflected).containsExactlyInAnyOrderElementsOf(PermissionCodes.ALL_PERMISSIONS);

    assertThat(reflected)
        .contains(
            "work-record:import",
            "work-record:export:async",
            "work-record:comment",
            "work-record:attachment",
            "work-record:relation")
        .hasSize(41);
  }

  @Test
  void permissionCodesFollowNamingConvention() {
    assertThat(PermissionCodes.ALL_PERMISSIONS)
        .allMatch(code -> code.matches("^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$"));
  }

  @Test
  void systemAdminMustRetainLegacyAiOpsPermissions() {
    assertThat(PermissionCodes.ALL_PERMISSIONS)
        .contains(
            "datasource:read",
            "alert:read",
            "incident:diagnose",
            "automation:approve",
            "audit:read",
            "admin:manage",
            "work-record:export");
  }

  @Test
  void phase13PermissionsAreComplete() {
    assertThat(PermissionCodes.PHASE_13_PERMISSIONS).hasSize(12);
    assertThat(PermissionCodes.PORTAL_IAM_PERMISSIONS).hasSize(7);
    assertThat(PermissionCodes.PHASE_20_PERMISSIONS).hasSize(7);
  }

  @Test
  void legacyPermissionsAreComplete() {
    assertThat(PermissionCodes.LEGACY_AIOPS_PERMISSIONS).hasSize(15);
  }
}
