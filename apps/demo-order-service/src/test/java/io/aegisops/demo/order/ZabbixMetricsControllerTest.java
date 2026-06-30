package io.aegisops.demo.order;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ZabbixMetricsControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private FaultModeService faultModeService;

  @AfterEach
  void tearDown() {
    faultModeService.reset();
  }

  @Test
  void shouldExposeDefaultZabbixMetrics() throws Exception {
    mvc.perform(get("/zabbix/health-status"))
        .andExpect(status().isOk())
        .andExpect(content().string("1"));

    mvc.perform(get("/zabbix/cpu-util"))
        .andExpect(status().isOk())
        .andExpect(content().string("15.00"));

    mvc.perform(get("/zabbix/order-create-time"))
        .andExpect(status().isOk())
        .andExpect(content().string("0.12"));
  }

  @Test
  void shouldExposeIncidentMetricsAfterFaultInjection() throws Exception {
    mvc.perform(post("/demo/fault/incident")).andExpect(status().isOk());

    mvc.perform(get("/zabbix/health-status"))
        .andExpect(status().isOk())
        .andExpect(content().string("0"));

    mvc.perform(get("/zabbix/cpu-util"))
        .andExpect(status().isOk())
        .andExpect(content().string("95.00"));

    mvc.perform(get("/zabbix/order-create-time"))
        .andExpect(status().isOk())
        .andExpect(content().string("2.50"));

    mvc.perform(get("/zabbix/error-count"))
        .andExpect(status().isOk())
        .andExpect(content().string("5"));
  }
}
