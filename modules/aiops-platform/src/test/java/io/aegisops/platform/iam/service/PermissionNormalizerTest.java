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
  void should_include_transitive_dependencies() {
    PermissionDefinitionRepository repo = Mockito.mock(PermissionDefinitionRepository.class);
    Mockito.when(repo.listAll())
        .thenReturn(
            List.of(
                def("work-record:read:all", "work-record", PermissionRisk.NORMAL, Set.of()),
                def(
                    "work-record:ai:generate",
                    "work-record",
                    PermissionRisk.HIGH,
                    Set.of("work-record:read:all")),
                def(
                    "work-record:ai:review",
                    "work-record",
                    PermissionRisk.SENSITIVE,
                    Set.of("work-record:ai:generate"))));
    PermissionNormalizer normalizer = new PermissionNormalizer(repo);

    var result = normalizer.normalize(Set.of("work-record:ai:review"));

    assertThat(result.permissions())
        .containsExactlyInAnyOrder(
            "work-record:read:all", "work-record:ai:generate", "work-record:ai:review");
  }

  @Test
  void should_reject_disabled_transitive_dependency() {
    PermissionDefinitionRepository repo = Mockito.mock(PermissionDefinitionRepository.class);
    Mockito.when(repo.listAll())
        .thenReturn(
            List.of(
                new PermissionDefinition(
                    "work-record:read:all",
                    "work-record",
                    "read",
                    "",
                    PermissionRisk.NORMAL,
                    Set.of(),
                    0,
                    false),
                def(
                    "work-record:ai:generate",
                    "work-record",
                    PermissionRisk.HIGH,
                    Set.of("work-record:read:all"))));
    PermissionNormalizer normalizer = new PermissionNormalizer(repo);

    assertThatThrownBy(() -> normalizer.normalize(Set.of("work-record:ai:generate")))
        .isInstanceOf(IamDomainException.class);
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
