package io.aegisops.datasource;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import io.aegisops.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class DataSourceControllerTest {
  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void updateUsesCurrentTenantAndPathId() throws Exception {
    DataSourceService service = mock(DataSourceService.class);
    OffsetDateTime now = OffsetDateTime.parse("2026-07-18T00:00:00Z");
    when(service.update(eq("tenant-1"), eq("ds-1"), any()))
        .thenReturn(
            new DataSourceRecord(
                "ds-1",
                "tenant-1",
                "zabbix",
                "新名称",
                "https://new.example",
                "inactive",
                now,
                now,
                null));
    TenantContext.setTenantId("tenant-1");
    MockMvc mvc = standaloneSetup(new DataSourceController(service)).build();

    mvc.perform(
            put("/api/datasources/ds-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "新名称",
                      "zabbix": {"endpoint": "https://new.example"}
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("ds-1"))
        .andExpect(jsonPath("$.data.status").value("inactive"));

    verify(service).update(eq("tenant-1"), eq("ds-1"), any(UpdateDataSourceRequest.class));
  }
}
