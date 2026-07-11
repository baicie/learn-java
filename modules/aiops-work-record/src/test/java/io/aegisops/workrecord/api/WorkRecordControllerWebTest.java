package io.aegisops.workrecord.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.web.GlobalExceptionHandler;
import io.aegisops.workrecord.application.command.WorkRecordExportResult;
import io.aegisops.workrecord.application.service.WorkRecordExportService;
import io.aegisops.workrecord.application.service.WorkRecordHistoryService;
import io.aegisops.workrecord.application.service.WorkRecordListMetaService;
import io.aegisops.workrecord.application.service.WorkRecordQueryService;
import io.aegisops.workrecord.application.service.WorkRecordService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Controller MVC Slice 测试：覆盖 403/400/CSV Header 等 HTTP 契约。
 *
 * <p>不依赖 Phase 编号，描述的是长期不变的 HTTP 行为契约。
 */
@WebMvcTest(controllers = WorkRecordController.class)
@ContextConfiguration(
    classes = {
      WorkRecordController.class,
      GlobalExceptionHandler.class,
      WorkRecordControllerWebTest.MethodSecurityConfiguration.class
    })
class WorkRecordControllerWebTest {

  @MockitoBean private WorkRecordService recordService;
  @MockitoBean private WorkRecordQueryService queryService;
  @MockitoBean private WorkRecordListMetaService metaService;
  @MockitoBean private WorkRecordExportService exportService;
  @MockitoBean private WorkRecordHistoryService historyService;

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void setTenant() {
    TenantContext.setTenantId("tenant-1");
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  @WithMockUser(authorities = {"work-record:read:self"})
  void selfReaderCanOpenList() throws Exception {
    when(queryService.page(eq("tenant-1"), any(), any()))
        .thenReturn(new PageResult<>(0, 1, 20, List.of()));

    mockMvc
        .perform(get("/api/work-record/records"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @WithMockUser(authorities = {"work-record:read:self"})
  void readerWithoutWritePermissionCannotCreate() throws Exception {
    mockMvc
        .perform(
            post("/api/work-record/records")
                .with(csrf())
                .contentType("application/json")
                .content(creationPayload()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(recordService);
  }

  @Test
  @WithMockUser(authorities = {"work-record:read:self"})
  void malformedDynamicFiltersReturn400() throws Exception {
    mockMvc
        .perform(get("/api/work-record/records").param("dynamicFilters", "not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"));

    verifyNoInteractions(queryService);
  }

  @Test
  @WithMockUser(authorities = {"work-record:write"})
  void recordTimeWithoutOffsetReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/work-record/records")
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "templateId": "template-1",
                      "templateVersionId": "version-1",
                      "title": "日报",
                      "status": "done",
                      "recordTime": "2026-07-11T10:00:00",
                      "builtinDataJson": "{}",
                      "customDataJson": "{}"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("recordTime must be ISO offset datetime"));

    verifyNoInteractions(recordService);
  }

  @Test
  @WithMockUser(authorities = {"work-record:export"})
  void exportReturnsCsvContract() throws Exception {
    when(exportService.export(eq("tenant-1"), any(), anyList(), any()))
        .thenReturn(
            new WorkRecordExportResult(
                "records.csv",
                "\uFEFF\"标题\"\r\n\"日报\"\r\n".getBytes(StandardCharsets.UTF_8),
                1));

    mockMvc
        .perform(
            post("/api/work-record/records/export")
                .with(csrf())
                .contentType("application/json")
                .content(
                    """
                    {
                      "templateId": "template-1",
                      "quickView": "all",
                      "columns": ["title"]
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Export-Row-Count", "1"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition", org.hamcrest.Matchers.containsString("records.csv")))
        .andExpect(content().contentType("text/csv;charset=UTF-8"));
  }

  @Test
  @WithMockUser(authorities = {"work-record:read:self"})
  void historyEndpointAlsoRequiresReadPermission() throws Exception {
    when(queryService.get(eq("tenant-1"), eq("record-1"), any()))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));

    mockMvc
        .perform(get("/api/work-record/records/{recordId}/history", "record-1"))
        .andExpect(status().isForbidden());

    verifyNoInteractions(historyService);
  }

  private String creationPayload() {
    return """
        {
          "templateId": "template-1",
          "templateVersionId": "version-1",
          "title": "日报",
          "status": "done",
          "recordTime": "2026-07-11T10:00:00+08:00",
          "builtinDataJson": "{}",
          "customDataJson": "{}"
        }
        """;
  }

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}
}