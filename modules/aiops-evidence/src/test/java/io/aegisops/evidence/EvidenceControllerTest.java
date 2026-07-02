package io.aegisops.evidence;

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
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

class EvidenceControllerTest {
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void shouldListEvidence() throws Exception {
    ZabbixEvidenceCollectorService service = Mockito.mock(ZabbixEvidenceCollectorService.class);
    EvidenceOrchestrationService orchestrationService =
        Mockito.mock(EvidenceOrchestrationService.class);
    EvidenceCollectionTaskRepository taskRepository =
        Mockito.mock(EvidenceCollectionTaskRepository.class);

    Mockito.when(service.list(eq("tenant_1"), eq("inc_1")))
        .thenReturn(
            List.of(
                new DiagnosisEvidenceRecord(
                    "evd_1",
                    "tenant_1",
                    "inc_1",
                    "zabbix:cpu:item_cpu",
                    "zabbix",
                    "metric_cpu_high",
                    "CPU 使用率持续高位",
                    "CPU 最大值 95%",
                    OffsetDateTime.parse("2026-06-21T05:00:00Z"),
                    OffsetDateTime.parse("2026-06-21T05:30:00Z"),
                    BigDecimal.valueOf(0.86),
                    "{}",
                    OffsetDateTime.parse("2026-06-21T05:31:00Z"),
                    OffsetDateTime.parse("2026-06-21T05:31:00Z"))));

    TenantContext.setTenantId("tenant_1");
    MockMvc mvc =
        standaloneSetup(
                new EvidenceController(service, orchestrationService, taskRepository))
            .setMessageConverters(jsonConverter())
            .build();

    mvc.perform(get("/api/incidents/inc_1/evidence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].evidenceType").value("metric_cpu_high"));
  }

  @Test
  void shouldCollectZabbixEvidence() throws Exception {
    ZabbixEvidenceCollectorService service = Mockito.mock(ZabbixEvidenceCollectorService.class);
    EvidenceOrchestrationService orchestrationService =
        Mockito.mock(EvidenceOrchestrationService.class);
    EvidenceCollectionTaskRepository taskRepository =
        Mockito.mock(EvidenceCollectionTaskRepository.class);

    Mockito.when(orchestrationService.collect(eq("tenant_1"), eq("inc_1"), any()))
        .thenReturn(
            new EvidenceCollectResponse(
                "inc_1", 6, 30, 0, 4, 4, 4, 0, "Collected"));

    TenantContext.setTenantId("tenant_1");
    MockMvc mvc =
        standaloneSetup(
                new EvidenceController(service, orchestrationService, taskRepository))
            .setMessageConverters(jsonConverter())
            .build();

    mvc.perform(
            post("/api/incidents/inc_1/evidence/zabbix/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "lookbackMinutes": 30
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.incidentId").value("inc_1"))
        .andExpect(jsonPath("$.data.itemsMatched").value(6))
        .andExpect(jsonPath("$.data.evidenceCreated").value(4));
  }

  private MappingJackson2HttpMessageConverter jsonConverter() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setObjectMapper(objectMapper);
    return converter;
  }
}
