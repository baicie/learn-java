package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.ConflictException;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.HandoverRepository;
import io.aegisops.workrecord.domain.model.HandoverStatus;
import io.aegisops.workrecord.domain.model.WorkRecordHandover;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRecordHandoverServiceTest {
  @Test
  void reportsOptimisticLockConflictDuringSubmit() {
    HandoverRepository repository = mock(HandoverRepository.class);
    WorkRecordQueryService records = mock(WorkRecordQueryService.class);
    var current =
        new WorkRecordHandover(
            "handover-1",
            "tenant-1",
            "user-1",
            "user-2",
            OffsetDateTime.parse("2026-07-14T00:00:00Z"),
            OffsetDateTime.parse("2026-07-14T08:00:00Z"),
            HandoverStatus.DRAFT,
            "交接",
            List.of(),
            List.of(),
            "user-1",
            OffsetDateTime.parse("2026-07-14T08:00:00Z"),
            null,
            null,
            1);
    when(repository.find("tenant-1", "handover-1")).thenReturn(Optional.of(current));
    when(repository.transition(
            "tenant-1", "handover-1", HandoverStatus.DRAFT, HandoverStatus.SUBMITTED, 1))
        .thenReturn(false);
    var service = new WorkRecordHandoverService(repository, records);

    assertThatThrownBy(() -> service.submit("tenant-1", "handover-1", 1, principal()))
        .isInstanceOf(ConflictException.class);
  }

  private static UserPrincipal principal() {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        Set.of("work-record:handover"),
        Map.of());
  }
}
