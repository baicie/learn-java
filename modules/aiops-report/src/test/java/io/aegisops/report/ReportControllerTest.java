package io.aegisops.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.common.tenant.TenantContext;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

class ReportControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void shouldGenerateReport() throws Exception {
    TenantContext.setTenantId("tenant_1");
    ReportService service = Mockito.mock(ReportService.class);
    Mockito.when(service.generate(eq("tenant_1"), eq("inc_1"), any())).thenReturn(response());

    MockMvc mvc =
        standaloneSetup(new ReportController(service))
            .setMessageConverters(jsonConverter())
            .build();

    mvc.perform(
            post("/api/incidents/inc_1/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "force": true,
                      "locale": "zh-CN",
                      "createdBy": "tester"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("rpt_1"))
        .andExpect(jsonPath("$.data.markdownContent").value("# report"));
  }

  @Test
  void shouldReturnLatestReport() throws Exception {
    TenantContext.setTenantId("tenant_1");
    ReportService service = Mockito.mock(ReportService.class);
    Mockito.when(service.latest("tenant_1", "inc_1")).thenReturn(response());

    MockMvc mvc =
        standaloneSetup(new ReportController(service))
            .setMessageConverters(jsonConverter())
            .build();

    mvc.perform(get("/api/incidents/inc_1/reports/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("rpt_1"))
        .andExpect(jsonPath("$.data.versionNo").value(1));
  }

  private MappingJackson2HttpMessageConverter jsonConverter() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setObjectMapper(objectMapper);
    return converter;
  }

  private IncidentReportResponse response() {
    return new IncidentReportResponse(
        "rpt_1",
        "inc_1",
        1,
        "incident_markdown",
        "markdown",
        "report",
        "# report",
        "{}",
        "tester",
        OffsetDateTime.parse("2026-06-21T05:30:00Z"),
        OffsetDateTime.parse("2026-06-21T05:30:00Z"));
  }
}
