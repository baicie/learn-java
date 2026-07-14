package io.aegisops.platform.iam.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.audit.PlatformAuditService;
import io.aegisops.platform.iam.domain.CreateRoleData;
import io.aegisops.platform.iam.domain.PlatformRole;
import io.aegisops.platform.iam.domain.PlatformRoleDetail;
import io.aegisops.platform.iam.domain.ReplaceRoleDataScopesCommand;
import io.aegisops.platform.iam.domain.ReplaceRolePermissionsCommand;
import io.aegisops.platform.iam.domain.UpdateRoleData;
import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.repository.PermissionDefinitionRepository;
import io.aegisops.platform.iam.repository.PlatformRoleRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformRoleServiceTest {
  private PlatformRoleRepository roles;
  private PermissionNormalizer normalizer;
  private PlatformRoleService service;

  @BeforeEach
  void setUp() {
    roles = mock(PlatformRoleRepository.class);
    normalizer = mock(PermissionNormalizer.class);
    service =
        new PlatformRoleService(
            roles,
            mock(PermissionDefinitionRepository.class),
            normalizer,
            mock(PlatformAuditService.class));
  }

  @Test
  void createAndUpdatePersistNormalizedPermissions() {
    normalized(Set.of("read"), Set.of());
    PlatformRole created =
        new PlatformRole("ops", "Ops", null, false, true, Set.of("read"), Map.of(), 1);
    when(roles.insert("ops", "Ops", null, false, true)).thenReturn(Optional.of(created));
    service.create(new CreateRoleData("ops", "Ops", null, false, true, Set.of("read")), "a");
    verify(roles).replacePermissions("ops", Set.of("read"));

    PlatformRoleDetail detail = detail(false, 0, Set.of("read"));
    when(roles.findByCode("ops")).thenReturn(Optional.of(detail));
    service.update("ops", new UpdateRoleData("Ops 2", null, true, Set.of("read")), "a");
    verify(roles).update("ops", "Ops 2", null, true, 1);
  }

  @Test
  void dangerousAndDestructivePermissionChangesAreGuarded() {
    when(roles.findByCode("ops")).thenReturn(Optional.of(detail(false, 1, Set.of("read"))));
    when(roles.userCount("ops")).thenReturn(1);
    normalized(Set.of("critical"), Set.of("critical"));
    assertThatThrownBy(
            () ->
                service.replacePermissions(
                    "ops",
                    new ReplaceRolePermissionsCommand(
                        Set.of("critical"),
                        new ReplaceRolePermissionsCommand.Confirmation("", false)),
                    "a"))
        .isInstanceOf(IllegalArgumentException.class);

    normalized(Set.of(), Set.of());
    assertThatThrownBy(
            () ->
                service.replacePermissions(
                    "ops",
                    new ReplaceRolePermissionsCommand(
                        Set.of(), new ReplaceRolePermissionsCommand.Confirmation("reason", true)),
                    "a"))
        .isInstanceOf(IamDomainException.class);
  }

  @Test
  void dataScopesAndDeleteUseRoleGuards() {
    when(roles.findByCode("ops")).thenReturn(Optional.of(detail(false, 0, Set.of())));
    service.replaceDataScopes(
        "ops",
        new ReplaceRoleDataScopesCommand(
            List.of(new PlatformRole.RoleDataScope("record", null, null)), 1),
        "a");
    verify(roles)
        .replaceDataScopes(
            "ops", List.of(new PlatformRole.RoleDataScope("record", "SELF", Map.of())));
    service.delete("ops", "a");

    when(roles.findByCode("system")).thenReturn(Optional.of(detail(true, 0, Set.of())));
    assertThatThrownBy(() -> service.delete("system", "a")).isInstanceOf(IamDomainException.class);
    when(roles.findByCode("busy")).thenReturn(Optional.of(detail(false, 2, Set.of())));
    assertThatThrownBy(() -> service.delete("busy", "a")).isInstanceOf(IamDomainException.class);
  }

  @Test
  void missingAndDuplicateRolesFail() {
    assertThatThrownBy(() -> service.detail("none")).isInstanceOf(IamDomainException.class);
    when(roles.exists("ops")).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.create(new CreateRoleData("ops", "Ops", null, false, true, Set.of()), "a"))
        .isInstanceOf(IamDomainException.class);
  }

  private void normalized(Set<String> permissions, Set<String> critical) {
    when(normalizer.normalize(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new PermissionNormalizer.NormalizedPermissionSet(permissions, permissions, critical));
  }

  private PlatformRoleDetail detail(boolean system, int users, Set<String> permissions) {
    return new PlatformRoleDetail(
        system ? "system" : "ops", "Ops", null, system, true, permissions, Map.of(), users, 1);
  }
}
