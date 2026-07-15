package io.aegisops.workrecord.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegisops.common.api.PageResult;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.web.GlobalExceptionHandler;
import io.aegisops.workrecord.application.command.AsyncJobQuery;
import io.aegisops.workrecord.application.service.AsyncJobDownloadService;
import io.aegisops.workrecord.application.service.AsyncJobService;
import io.aegisops.workrecord.domain.model.AsyncJob;
import io.aegisops.workrecord.domain.model.AsyncJobStatus;
import io.aegisops.workrecord.domain.model.AsyncJobType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AsyncJobController.class)
@ContextConfiguration(
    classes = {
      AsyncJobController.class,
      GlobalExceptionHandler.class,
      AsyncJobControllerWebTest.MethodSecurityConfiguration.class
    })
class AsyncJobControllerWebTest {
  @MockitoBean private AsyncJobService service;
  @MockitoBean private AsyncJobDownloadService downloads;
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
  void selfReaderOnlyRequestsOwnJobs() throws Exception {
    UserPrincipal principal = principal(Set.of("work-record:read:self"));
    when(service.page(eq("tenant-1"), any(AsyncJobQuery.class)))
        .thenReturn(PageResult.empty(1, 20));

    mockMvc
        .perform(get("/api/work-record/async-jobs").with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(0));

    verify(service).page("tenant-1", new AsyncJobQuery("user-1", false, null, null, null, null));
  }

  @Test
  void userWithoutWorkRecordReadPermissionIsForbidden() throws Exception {
    mockMvc
        .perform(get("/api/work-record/async-jobs").with(user(principal(Set.of()))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(service);
  }

  @Test
  void responseDoesNotExposeTenantOrObjectStorageKeys() throws Exception {
    UserPrincipal principal = principal(Set.of("work-record:read:self"));
    when(service.page(eq("tenant-1"), any(AsyncJobQuery.class)))
        .thenReturn(new PageResult<>(1, 1, 20, List.of(job())));

    mockMvc
        .perform(get("/api/work-record/async-jobs").with(user(principal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").value("job-1"))
        .andExpect(jsonPath("$.data.items[0].tenantId").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].sourceObjectKey").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].resultObjectKey").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].errorObjectKey").doesNotExist())
        .andExpect(jsonPath("$.data.items[0].idempotencyKey").doesNotExist());
  }

  @Test
  void cancelRequiresCsrf() throws Exception {
    mockMvc
        .perform(
            post("/api/work-record/async-jobs/job-1/cancel")
                .with(user(principal(Set.of("work-record:read:self")))))
        .andExpect(status().isForbidden());
    verifyNoInteractions(service);
  }

  private static UserPrincipal principal(Set<String> permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        permissions,
        Map.of());
  }

  private static AsyncJob job() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-14T12:00:00+08:00");
    return new AsyncJob(
        "job-1",
        "tenant-1",
        AsyncJobType.EXCEL_EXPORT,
        AsyncJobStatus.SUCCEEDED,
        "user-1",
        "{}",
        "{}",
        "tenant-1/import/source.xlsx",
        3,
        3,
        3,
        0,
        "tenant-1/export/result.xlsx",
        "result.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "tenant-1/import/errors.xlsx",
        "secret-idempotency-key",
        null,
        2,
        now,
        now,
        now.plusDays(7),
        now,
        now);
  }

  @Configuration
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}
}
