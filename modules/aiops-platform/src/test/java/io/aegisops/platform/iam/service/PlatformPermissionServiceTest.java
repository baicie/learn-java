package io.aegisops.platform.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.platform.iam.domain.PermissionDefinition;
import io.aegisops.platform.iam.repository.PermissionDefinitionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformPermissionServiceTest {

  @Test
  void listReturnsCompletePermissionCatalog() {
    PermissionDefinitionRepository repository = mock(PermissionDefinitionRepository.class);
    List<PermissionDefinition> catalog = List.of();
    when(repository.list(null)).thenReturn(catalog);

    assertThat(new PlatformPermissionService(repository).list()).isSameAs(catalog);
    verify(repository).list(null);
  }
}
