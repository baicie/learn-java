package io.aegisops.platform.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.domain.PermissionRisk;
import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.repository.PermissionDefinitionRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PermissionNormalizerTest {

  @Test
  void should_include_dependencies_when_requesting_high_risk_permission() {
    PermissionDefinitionRepository repo = Mockito.mock(PermissionDefinitionRepository.class);
    Mockito.when(repo.listAll())
        .thenReturn(
            List.of(
                def("platform:user:read", "platform-user", PermissionRisk.SENSITIVE, Set.of()),
                def(
                    "platform:user:write",
                    "platform-user",
                    PermissionRisk.HIGH,
                    Set.of("platform:user:read"))));
    PermissionNormalizer normalizer = new PermissionNormalizer(repo);

    var result = normalizer.normalize(Set.of("platform:user:write"));

    assertThat(result.permissions())
        .containsExactlyInAnyOrder("platform:user:read", "platform:user:write");
    assertThat(result.criticalCodes()).isEmpty();
  }

  @Test
  void should_flag_critical_permissions_separately() {
    PermissionDefinitionRepository repo = Mockito.mock(PermissionDefinitionRepository.class);
    Mockito.when(repo.listAll())
        .thenReturn(
            List.of(
                def("platform:user:read", "platform-user", PermissionRisk.SENSITIVE, Set.of()),
                def(
                    "platform:user:status",
                    "platform-user",
                    PermissionRisk.CRITICAL,
                    Set.of("platform:user:read"))));
    PermissionNormalizer normalizer = new PermissionNormalizer(repo);

    var result = normalizer.normalize(Set.of("platform:user:status", "platform:user:read"));

    assertThat(result.permissions()).contains("platform:user:status", "platform:user:read");
    assertThat(result.criticalCodes()).containsOnly("platform:user:status");
  }

  @Test
  void should_reject_unknown_permissions() {
    PermissionDefinitionRepository repo = Mockito.mock(PermissionDefinitionRepository.class);
    Mockito.when(repo.listAll())
        .thenReturn(
            List.of(
                def("platform:user:read", "platform-user", PermissionRisk.SENSITIVE, Set.of())));
    PermissionNormalizer normalizer = new PermissionNormalizer(repo);

    assertThatThrownBy(() -> normalizer.normalize(Set.of("platform:user:nonexistent")))
        .isInstanceOf(IamDomainException.class);
  }

  private static PermissionDefinition def(
      String code, String module, PermissionRisk risk, Set<String> deps) {
    return new PermissionDefinition(code, module, code.split(":")[2], "", risk, deps, 0, true);
  }
}
