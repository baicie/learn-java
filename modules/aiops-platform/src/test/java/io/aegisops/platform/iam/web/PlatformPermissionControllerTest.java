package io.aegisops.platform.iam.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.domain.PermissionRisk;
import io.aegisops.platform.iam.service.PlatformPermissionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformPermissionControllerTest {

  @Test
  void listGroupsPermissionCatalogForPortal() {
    PlatformPermissionService service = mock(PlatformPermissionService.class);
    when(service.list())
        .thenReturn(
            List.of(
                permission("platform:user:read", "platform-user", "查看用户", PermissionRisk.NORMAL),
                permission(
                    "platform:user:write", "platform-user", "维护用户", PermissionRisk.SENSITIVE),
                permission("platform:role:read", "platform-role", "查看角色", PermissionRisk.NORMAL),
                permission("custom:read", "custom", "查看自定义资源", PermissionRisk.HIGH)));

    List<PlatformPermissionController.PermissionModuleResponse> result =
        new PlatformPermissionController(service).list();

    assertThat(result).hasSize(3);
    assertThat(result.get(0).moduleCode()).isEqualTo("platform-user");
    assertThat(result.get(0).moduleName()).isEqualTo("用户管理");
    assertThat(result.get(0).children())
        .extracting(PlatformPermissionController.PermissionResponse::code)
        .containsExactly("platform:user:read", "platform:user:write");
    assertThat(result.get(1).moduleName()).isEqualTo("权限管理");
    assertThat(result.get(2).moduleName()).isEqualTo("custom");
    assertThat(result.get(2).children().getFirst().riskLevel()).isEqualTo("high");
  }

  private static PermissionDefinition permission(
      String code, String moduleCode, String name, PermissionRisk risk) {
    return new PermissionDefinition(
        code, moduleCode, name, name + "说明", risk, Set.of("auth:login"), 10, true);
  }
}
