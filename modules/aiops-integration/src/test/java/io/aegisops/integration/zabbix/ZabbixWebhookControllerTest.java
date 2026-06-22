package io.aegisops.integration.zabbix;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ZabbixWebhookControllerTest {

  private final ZabbixWebhookService service = org.mockito.Mockito.mock(ZabbixWebhookService.class);
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ZabbixWebhookController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
  }

  @Test
  void shouldReturnHealth() throws Exception {
    mvc.perform(get("/api/integrations/zabbix/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value("zabbix integration is ready"));
  }

  @Test
  void shouldIngestWebhookWithHeaderToken() throws Exception {
    when(service.ingest(eq("ds_1"), eq("secret"), any(ZabbixWebhookPayload.class)))
        .thenReturn(
            new ZabbixWebhookIngestResponse(
                "alert_1", "ds_1", "tenant_1", "ds_1:20001", "open", true, "created"));

    mvc.perform(
            post("/api/integrations/zabbix/events")
                .queryParam("datasourceId", "ds_1")
                .header("X-AegisOps-Webhook-Token", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "eventId": "20001",
                      "status": "PROBLEM",
                      "severity": "High",
                      "title": "CPU High"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.alertId").value("alert_1"))
        .andExpect(jsonPath("$.data.datasourceId").value("ds_1"))
        .andExpect(jsonPath("$.data.status").value("open"))
        .andExpect(jsonPath("$.data.created").value(true));
  }

  @Test
  void shouldFallbackToQueryToken() throws Exception {
    when(service.ingest(eq("ds_1"), eq("secret"), any(ZabbixWebhookPayload.class)))
        .thenReturn(
            new ZabbixWebhookIngestResponse(
                "alert_1", "ds_1", "tenant_1", "ds_1:20001", "open", true, "created"));

    mvc.perform(
            post("/api/integrations/zabbix/events")
                .queryParam("datasourceId", "ds_1")
                .queryParam("token", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "eventId": "20001",
                      "status": "PROBLEM",
                      "severity": "High",
                      "title": "CPU High"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.alertId").value("alert_1"));
  }
}
