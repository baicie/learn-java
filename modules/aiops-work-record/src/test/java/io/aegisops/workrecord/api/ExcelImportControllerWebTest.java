package io.aegisops.workrecord.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import io.aegisops.web.GlobalExceptionHandler;
import io.aegisops.workrecord.application.service.ExcelImportSubmissionService;
import io.aegisops.workrecord.application.service.ExcelImportTemplateService;
import io.aegisops.workrecord.application.service.UploadSessionService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ExcelImportController.class)
@ContextConfiguration(
    classes = {
      ExcelImportController.class,
      GlobalExceptionHandler.class,
      ExcelImportControllerWebTest.MethodSecurityConfiguration.class
    })
class ExcelImportControllerWebTest {
  @MockitoBean private UploadSessionService uploads;
  @MockitoBean private ExcelImportSubmissionService submissions;
  @MockitoBean private ExcelImportTemplateService importTemplates;
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
  void preparesDirectUploadForAuthorizedUser() throws Exception {
    when(uploads.prepareExcelImport(eq("tenant-1"), any(), any()))
        .thenReturn(
            new UploadSessionService.PreparedUpload(
                "upload-1", "https://minio/upload", OffsetDateTime.parse("2026-07-14T10:10Z")));

    mockMvc
        .perform(
            post("/api/work-record/imports/uploads")
                .with(user(principal(Set.of("work-record:import"))))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"originalFileName":"records.xlsx","contentType":"application/octet-stream","sizeBytes":1024}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.uploadId").value("upload-1"))
        .andExpect(jsonPath("$.data.uploadUrl").value("https://minio/upload"));
  }

  @Test
  void importPermissionIsRequiredBeforeServiceInvocation() throws Exception {
    mockMvc
        .perform(
            post("/api/work-record/imports/uploads")
                .with(user(principal(Set.of("work-record:write"))))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"originalFileName":"records.xlsx","contentType":"application/octet-stream","sizeBytes":1024}
                    """))
        .andExpect(status().isForbidden());

    verifyNoInteractions(uploads, submissions);
  }

  @Test
  void downloadsTemplateForSelectedTemplateVersion() throws Exception {
    byte[] workbook = {1, 2, 3};
    when(importTemplates.generate("tenant-1", "template-1", "version-1"))
        .thenReturn(
            new ExcelImportTemplateService.ExcelImportTemplate(
                workbook, "日报-导入模板-v1.xlsx"));

    mockMvc
        .perform(
            get("/api/work-record/imports/template")
                .param("templateId", "template-1")
                .param("templateVersionId", "version-1")
                .with(user(principal(Set.of("work-record:import")))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .andExpect(
            header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.CONTENT_DISPOSITION,
                    containsString(
                        "%E6%97%A5%E6%8A%A5-%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF-v1.xlsx")))
        .andExpect(content().bytes(workbook));
  }

  @Test
  void importPermissionIsRequiredToDownloadTemplate() throws Exception {
    mockMvc
        .perform(
            get("/api/work-record/imports/template")
                .param("templateId", "template-1")
                .param("templateVersionId", "version-1")
                .with(user(principal(Set.of("work-record:write")))))
        .andExpect(status().isForbidden());

    verifyNoInteractions(importTemplates);
  }

  private static UserPrincipal principal(Set<String> permissions) {
    return new UserPrincipal(
        new UserPrincipal.Identity("user-1", "tenant-1", "alice", "Alice"),
        Set.of(),
        permissions,
        Map.of());
  }

  @Configuration
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}
}
