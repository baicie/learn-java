package io.aegisops.workrecord.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegisops.common.tenant.TenantContext;
import io.aegisops.web.GlobalExceptionHandler;
import io.aegisops.workrecord.application.service.WorkRecordUserLookupService;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(controllers = WorkRecordUserLookupController.class)
@ContextConfiguration(
    classes = {
      WorkRecordUserLookupController.class,
      GlobalExceptionHandler.class,
      WorkRecordUserLookupControllerWebTest.MethodSecurityConfiguration.class
    })
class WorkRecordUserLookupControllerWebTest {
  @MockitoBean private WorkRecordUserLookupService service;
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
  void readerCanResolveDisplayNames() throws Exception {
    when(service.displayNames(eq("tenant-1"), eq(List.of("u1", "u2"))))
        .thenReturn(Map.of("u1", "张三", "u2", "李四"));

    mockMvc
        .perform(
            get("/api/work-record/users/display-names")
                .param("ids", "u1", "u2")
                .with(user("reader").authorities(() -> "work-record:read:self")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.u1").value("张三"));

    verify(service).displayNames("tenant-1", List.of("u1", "u2"));
  }

  @Test
  void userWithoutRecordReadPermissionIsDenied() throws Exception {
    mockMvc
        .perform(
            get("/api/work-record/users/display-names")
                .param("ids", "u1")
                .with(user("writer").authorities(() -> "work-record:write")))
        .andExpect(status().isForbidden());
  }

  @Configuration
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}
}
