package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkflowRepository;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowConfigurationServiceTest {
  @Test
  void createsVersionedOwnerApprovalConfiguration() {
    WorkflowRepository repository = mock(WorkflowRepository.class);
    when(repository.nextApprovalVersion("tenant-1", "template-1")).thenReturn(3);
    var service = new WorkflowConfigurationService(repository);

    String id =
        service.createApproval(
            "tenant-1",
            new WorkflowConfigurationService.ApprovalConfiguration(
                "template-1", "完成审批", "record_owner", null, 60),
            principal());

    assertThat(id).isNotBlank();
    verify(repository).disableApprovals("tenant-1", "template-1");
    verify(repository).createApprovalDefinition(any());
    verify(repository).createApprovalStep(any(), any(), any(), any(), any());
  }

  @Test
  void validatesSlaConfigurationBeforePersistence() {
    WorkflowRepository repository = mock(WorkflowRepository.class);
    var service = new WorkflowConfigurationService(repository);

    assertThatThrownBy(
            () ->
                service.createSla(
                    "tenant-1",
                    new WorkflowConfigurationService.SlaConfiguration(
                        "template-1", "完成时限", "draft", "draft", 0, true, "unknown"),
                    principal()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of(),
        Map.of());
  }
}
