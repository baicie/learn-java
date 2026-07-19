package io.aegisops.workrecord.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.workrecord.api.dto.RecordRequests.CreateRecordRequest;
import io.aegisops.workrecord.api.dto.RecordRequests.UpdateRecordRequest;
import io.aegisops.workrecord.application.service.WorkRecordExportService;
import io.aegisops.workrecord.application.service.WorkRecordListMetaService;
import io.aegisops.workrecord.application.service.WorkRecordQueryService;
import io.aegisops.workrecord.application.service.WorkRecordService;
import io.aegisops.workrecord.domain.model.RecordStatus;
import io.aegisops.workrecord.domain.model.WorkRecord;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkRecordControllerRecordTimeTest {
  private static final String TENANT = "t1";

  private final WorkRecordService recordService = mock(WorkRecordService.class);
  private final WorkRecordQueryService queryService = mock(WorkRecordQueryService.class);
  private final WorkRecordListMetaService metaService = mock(WorkRecordListMetaService.class);
  private final WorkRecordExportService exportService = mock(WorkRecordExportService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkRecordController controller =
      new WorkRecordController(
          recordService, queryService, metaService, exportService, objectMapper);

  @BeforeEach
  void setUp() {
    TenantContext.setTenantId(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldRejectCreateRecordTimeWithoutOffset() {
    CreateRecordRequest request =
        new CreateRecordRequest(
            "tpl1", "v1", "日报", "draft", null, "2026-01-01T10:00:00", "{}", "{}");

    assertThatThrownBy(() -> controller.create(request, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recordTime must be ISO offset datetime");
  }

  @Test
  void shouldRejectBlankCreateRecordTime() {
    CreateRecordRequest request =
        new CreateRecordRequest("tpl1", "v1", "日报", "draft", null, " ", "{}", "{}");

    assertThatThrownBy(() -> controller.create(request, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recordTime is required");
  }

  @Test
  void shouldRejectBlankUpdateRecordTime() {
    UpdateRecordRequest request = new UpdateRecordRequest(null, null, null, " ", null, null);

    assertThatThrownBy(() -> controller.update("r1", request, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recordTime must not be blank");
  }

  @Test
  void shouldAcceptCreateRecordTimeWithOffset() {
    WorkRecord saved = stubRecord();
    when(recordService.create(any(), any(), any())).thenReturn(saved);

    CreateRecordRequest request =
        new CreateRecordRequest(
            "tpl1", "v1", "日报", "draft", null, "2026-01-01T10:00:00Z", "{}", "{}");

    ApiResponse<WorkRecord> response = controller.create(request, null);

    assertThatCode(() -> verify(recordService).create(any(), any(), any()))
        .doesNotThrowAnyException();
    assertEquals("r1", response.data().id());
  }

  @Test
  void shouldRejectCreateWithoutTenant() {
    TenantContext.clear();
    CreateRecordRequest request =
        new CreateRecordRequest(
            "tpl1", "v1", "日报", "draft", null, "2026-01-01T10:00:00Z", "{}", "{}");

    assertThatThrownBy(() -> controller.create(request, null))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Tenant context is required");
  }

  private WorkRecord stubRecord() {
    return new WorkRecord(
        "r1",
        TENANT,
        "tpl1",
        "v1",
        "日报",
        RecordStatus.DRAFT,
        null,
        "u1",
        OffsetDateTime.parse("2026-01-01T10:00:00Z"),
        "{}",
        "{}",
        1,
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        null);
  }
}
