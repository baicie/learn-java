package io.aegisops.asset.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.aegisops.asset.api.dto.AssetPageResponse;
import io.aegisops.asset.api.dto.AssetResponse;
import io.aegisops.asset.application.AssetManagementService;
import io.aegisops.asset.application.AssetQuery;
import io.aegisops.asset.application.AssetQueryService;
import io.aegisops.common.tenant.TenantContext;
import java.time.OffsetDateTime;
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

@WebMvcTest(controllers = AssetController.class)
@ContextConfiguration(
    classes = {AssetController.class, AssetControllerTest.MethodSecurityConfiguration.class})
class AssetControllerTest {

  @MockitoBean private AssetQueryService queryService;
  @MockitoBean private AssetManagementService managementService;
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
  void readerCanListAssetsAndPageSizeIsCapped() throws Exception {
    when(queryService.page(eq("tenant-1"), any(AssetQuery.class)))
        .thenReturn(new AssetPageResponse(1, 1, 100, List.of(asset())));

    mockMvc
        .perform(
            get("/api/assets")
                .param("page", "1")
                .param("pageSize", "500")
                .param("assetType", "host")
                .param("sourceType", "zabbix")
                .param("keyword", "db")
                .with(user("reader").authorities(() -> "asset:read")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pageSize").value(100))
        .andExpect(jsonPath("$.data.items[0].id").value("asset-1"));

    verify(queryService).page("tenant-1", new AssetQuery(1, 100, "host", "zabbix", "db", null));
  }

  @Test
  void readerWithoutWritePermissionCannotCreate() throws Exception {
    mockMvc
        .perform(
            post("/api/assets")
                .with(user("reader").authorities(() -> "asset:read"))
                .with(csrf())
                .contentType("application/json")
                .content(createPayload()))
        .andExpect(status().isForbidden());

    verifyNoInteractions(managementService);
  }

  @Test
  void writerCreatesManualAssetInCurrentTenant() throws Exception {
    when(managementService.create(eq("tenant-1"), any(), eq("writer"))).thenReturn(asset());

    mockMvc
        .perform(
            post("/api/assets")
                .with(user("writer").authorities(() -> "asset:write"))
                .with(csrf())
                .contentType("application/json")
                .content(createPayload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("asset-1"));

    verify(managementService).create(eq("tenant-1"), any(), eq("writer"));
  }

  private String createPayload() {
    return """
        {
          "assetType": "host",
          "name": "db-prod-01",
          "displayName": "生产数据库一号",
          "environment": "production",
          "criticality": "tier-1",
          "ip": "10.0.0.8",
          "tags": {"role": "database"}
        }
        """;
  }

  private AssetResponse asset() {
    OffsetDateTime now = OffsetDateTime.parse("2026-07-16T10:00:00Z");
    return new AssetResponse(
        "asset-1",
        "host",
        "db-prod-01",
        "生产数据库一号",
        null,
        "production",
        "10.0.0.8",
        null,
        null,
        "tier-1",
        Map.of("role", "database"),
        "active",
        1,
        now,
        now,
        now,
        0);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class MethodSecurityConfiguration {}
}
