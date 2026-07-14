package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordCalendarPort;
import io.aegisops.workrecord.application.port.WorkflowRepository;
import io.aegisops.workrecord.application.port.WorkflowRepository.ApprovalTask;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class WorkRecordLifecycleCoordinatorTest {
  @Test
  void recordOwnerTaskRejectsNonOwner() {
    WorkflowRepository repository = mock(WorkflowRepository.class);
    when(repository.lockPendingTask("tenant-1", "task-1"))
        .thenReturn(
            Optional.of(
                new ApprovalTask("instance-1", "record-1", "owner-1", "record_owner", "owner")));
    var service =
        new WorkRecordLifecycleCoordinator(repository, mock(WorkRecordCalendarPort.class));

    assertThatThrownBy(() -> service.act("tenant-1", "task-1", true, null, user("other")))
        .isInstanceOf(AccessDeniedException.class);
    verify(repository).lockPendingTask("tenant-1", "task-1");
    verifyNoMoreInteractions(repository);
  }

  @Test
  void recordOwnerCanApproveAssignedTask() {
    WorkflowRepository repository = mock(WorkflowRepository.class);
    when(repository.lockPendingTask("tenant-1", "task-1"))
        .thenReturn(
            Optional.of(
                new ApprovalTask("instance-1", "record-1", "owner-1", "record_owner", "owner")));
    var service =
        new WorkRecordLifecycleCoordinator(repository, mock(WorkRecordCalendarPort.class));

    assertThat(service.act("tenant-1", "task-1", true, "ok", user("owner-1")))
        .isEqualTo("approved");
    verify(repository)
        .finishApproval(
            new WorkflowRepository.ApprovalAction(
                "tenant-1", "task-1", "instance-1", "record-1", true, "owner-1", "ok"));
  }

  private static UserPrincipal user(String id) {
    return new UserPrincipal(
        new UserPrincipal.Identity(id, "tenant-1", id, id), Set.of(), Set.of(), Map.of());
  }
}
