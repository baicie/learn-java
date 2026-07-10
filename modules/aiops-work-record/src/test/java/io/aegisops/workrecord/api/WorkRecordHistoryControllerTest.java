package io.aegisops.workrecord.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.audit.AuditEvent;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.service.WorkRecordExportService;
import io.aegisops.workrecord.application.service.WorkRecordHistoryService;
import io.aegisops.workrecord.application.service.WorkRecordListMetaService;
import io.aegisops.workrecord.application.service.WorkRecordQueryService;
import io.aegisops.workrecord.application.service.WorkRecordService;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

class WorkRecordHistoryControllerTest {
  private static final String TENANT = "t1";

  private final WorkRecordService recordService = mock(WorkRecordService.class);
  private final WorkRecordQueryService queryService = mock(WorkRecordQueryService.class);
  private final WorkRecordListMetaService metaService = mock(WorkRecordListMetaService.class);
  private final WorkRecordExportService exportService = mock(WorkRecordExportService.class);
  private final WorkRecordHistoryService historyService = mock(WorkRecordHistoryService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordController controller =
      new WorkRecordController(
          recordService, queryService, metaService, exportService, historyService, objectMapper);

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void historyMustCheckRecordPermissionBeforeReadingAudit() {
    UserPrincipal principal =
        new UserPrincipal(
            "u1", TENANT, "u1", "alice", Set.of("admin"), Set.of("work-record:read:all"), Map.of());
    WorkRecord record = stubRecord("r1");

    when(queryService.get(TENANT, "r1", principal)).thenReturn(record);
    when(historyService.list(TENANT, "r1")).thenReturn(List.of());

    controller.history("r1", principal);

    InOrder order = inOrder(queryService, historyService);
    order.verify(queryService).get(TENANT, "r1", principal);
    order.verify(historyService).list(TENANT, "r1");
  }

  @Test
  void unauthorizedRecordMustNotExposeHistory() {
    UserPrincipal principal =
        new UserPrincipal("u1", TENANT, "u1", "alice", Set.of("viewer"), Set.of(), Map.of());

    when(queryService.get(eq(TENANT), eq("r1"), any()))
        .thenThrow(new AccessDeniedException("forbidden"));

    assertThatThrownBy(() -> controller.history("r1", principal))
        .isInstanceOf(AccessDeniedException.class);

    verifyNoInteractions(historyService);
  }

  @Test
  void historyReturnsAuditEventsWhenAuthorized() {
    UserPrincipal principal =
        new UserPrincipal(
            "u1", TENANT, "u1", "alice", Set.of("admin"), Set.of("work-record:read:all"), Map.of());
    WorkRecord record = stubRecord("r1");
    AuditEvent event =
        new AuditEvent(
            "a1",
            TENANT,
            "u1",
            "work_record.record.update",
            "work_record",
            "r1",
            "{\"title\":\"旧\"}",
            "{\"title\":\"新\"}",
            "{\"changes\":[]}",
            OffsetDateTime.now());

    when(queryService.get(TENANT, "r1", principal)).thenReturn(record);
    when(historyService.list(TENANT, "r1")).thenReturn(List.of(event));

    var response = controller.history("r1", principal);

    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).id()).isEqualTo("a1");
  }

  private WorkRecord stubRecord(String recordId) {
    return new WorkRecord(
        recordId,
        TENANT,
        "tpl1",
        "v1",
        "日报",
        RecordStatus.DRAFT,
        "u1",
        "u1",
        OffsetDateTime.parse("2026-01-01T00:00:00Z"),
        "{}",
        "{}",
        1,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }
}
