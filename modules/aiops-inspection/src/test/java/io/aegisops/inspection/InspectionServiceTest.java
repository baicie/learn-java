package io.aegisops.inspection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class InspectionServiceTest {
  @Test
  void create_shouldValidateName() {
    InspectionService service = new InspectionService(null);
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateInspectionTaskRequest("", "HOST", "{}", "host-basic"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void create_shouldValidateTargetType() {
    InspectionService service = new InspectionService(null);
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateInspectionTaskRequest("Host Check", "", "{}", "host-basic"),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("targetType");
  }

  @Test
  void create_shouldValidateTemplateKey() {
    InspectionService service = new InspectionService(null);
    assertThatThrownBy(
            () ->
                service.create(
                    "t1",
                    new CreateInspectionTaskRequest("Host Check", "HOST", "{}", ""),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("templateKey");
  }

  @Test
  void create_shouldDelegateToRepository() {
    InspectionRepository repository = org.mockito.Mockito.mock(InspectionRepository.class);
    InspectionService service = new InspectionService(repository);
    CreateInspectionTaskRequest request =
        new CreateInspectionTaskRequest("主机基础巡检", "HOST", "{}", "host-basic");

    service.create("t1", request, "u1");

    verify(repository).create("t1", request, "u1");
  }

  @Test
  void create_shouldDefaultCreatedByToSystem() {
    InspectionRepository repository = org.mockito.Mockito.mock(InspectionRepository.class);
    InspectionService service = new InspectionService(repository);
    CreateInspectionTaskRequest request =
        new CreateInspectionTaskRequest("主机基础巡检", "HOST", "{}", "host-basic");

    service.create("t1", request, null);

    verify(repository).create("t1", request, "system");
  }
}
