package io.aegisops.workrecord.application.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.aegisops.common.api.PageResult;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.RecordQuery;
import io.aegisops.workrecord.application.port.WorkRecordRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class WorkRecordQueryServiceQuickViewTest {
  private final WorkRecordRepository repository = mock(WorkRecordRepository.class);
  private final WorkRecordQueryService service =
      new WorkRecordQueryService(repository, new WorkRecordPermissionService());

  @Test
  void shouldApplyMineQuickView() {
    Mockito.when(repository.page(eq("t1"), Mockito.any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    service.page("t1", query("mine"), user());

    ArgumentCaptor<RecordQuery> captor = ArgumentCaptor.forClass(RecordQuery.class);
    verify(repository).page(eq("t1"), captor.capture());
    org.junit.jupiter.api.Assertions.assertEquals("u1", captor.getValue().ownerId());
  }

  private RecordQuery query(String quickView) {
    return new RecordQuery(
        1,
        20,
        null,
        null,
        List.of(),
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        List.of(),
        "recordTime",
        "desc",
        quickView,
        null);
  }

  private UserPrincipal user() {
    return new UserPrincipal(
        "u1",
        "t1",
        "alice",
        "Alice",
        Set.of("admin"));
  }
}