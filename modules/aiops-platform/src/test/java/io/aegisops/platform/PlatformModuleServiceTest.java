package io.aegisops.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformModuleServiceTest {
  @Mock private PlatformModuleRepository repository;

  @Test
  void listModules_shouldReturnAllModules() {
    PlatformModule module =
        new PlatformModule("1", "platform", "平台底座", "1.0.0", true, "HEALTHY", "{}", null);
    when(repository.findAll()).thenReturn(List.of(module));

    PlatformModuleService service = new PlatformModuleService(repository);
    var result = service.listModules();

    assertThat(result).hasSize(1);
    assertThat(result.get(0).moduleId()).isEqualTo("platform");
  }

  @Test
  void getOrCreatePlatform_whenNotExists_shouldCreate() {
    when(repository.findByModuleId("platform")).thenReturn(Optional.empty());
    when(repository.create("platform", "平台底座", "1.0.0"))
        .thenReturn(
            new PlatformModule("mod-1", "platform", "平台底座", "1.0.0", true, "HEALTHY", "{}", null));

    PlatformModuleService service = new PlatformModuleService(repository);
    var result = service.getOrCreatePlatform();

    assertThat(result.moduleId()).isEqualTo("platform");
    verify(repository).create("platform", "平台底座", "1.0.0");
  }

  @Test
  void getOrCreatePlatform_whenExists_shouldReturnExisting() {
    PlatformModule existing =
        new PlatformModule("1", "platform", "平台底座", "1.0.0", true, "HEALTHY", "{}", null);
    when(repository.findByModuleId("platform")).thenReturn(Optional.of(existing));

    PlatformModuleService service = new PlatformModuleService(repository);
    var result = service.getOrCreatePlatform();

    assertThat(result.moduleId()).isEqualTo("platform");
    verify(repository, never()).create(anyString(), anyString(), anyString());
  }
}
