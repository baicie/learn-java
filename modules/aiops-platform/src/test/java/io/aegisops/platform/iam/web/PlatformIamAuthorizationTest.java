package io.aegisops.platform.iam.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PlatformIamAuthorizationTest {

  @Test
  void userEndpointsRequireTheirSpecificAuthorities() throws Exception {
    assertAuthority(PlatformUserController.class, "list", "platform:user:read");
    assertAuthority(PlatformUserController.class, "findById", "platform:user:read");
    assertAuthority(PlatformUserController.class, "create", "platform:user:write");
    assertAuthority(PlatformUserController.class, "update", "platform:user:write");
    assertAuthority(PlatformUserController.class, "changeStatus", "platform:user:status");
    assertAuthority(PlatformUserController.class, "resetPassword", "platform:user:reset-password");
    assertAuthority(PlatformUserController.class, "replaceRoles", "platform:user:assign-role");
  }

  @Test
  void roleEndpointsRequireTheirSpecificAuthorities() throws Exception {
    assertAuthority(PlatformRoleController.class, "list", "platform:role:read");
    assertAuthority(PlatformRoleController.class, "detail", "platform:role:read");
    assertAuthority(PlatformRoleController.class, "create", "platform:role:write");
    assertAuthority(PlatformRoleController.class, "update", "platform:role:write");
    assertAuthority(PlatformRoleController.class, "replacePermissions", "platform:role:write");
    assertAuthority(PlatformRoleController.class, "replaceDataScopes", "platform:role:write");
    assertAuthority(PlatformRoleController.class, "delete", "platform:role:write");
    assertAuthority(PlatformPermissionController.class, "list", "platform:role:read");
  }

  private static void assertAuthority(Class<?> controller, String methodName, String authority)
      throws Exception {
    Method method =
        java.util.Arrays.stream(controller.getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(methodName))
            .findFirst()
            .orElseThrow(NoSuchMethodException::new);
    PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
    assertThat(annotation).as(controller.getSimpleName() + "." + methodName).isNotNull();
    assertThat(annotation.value()).contains("hasAuthority('" + authority + "')");
  }
}
