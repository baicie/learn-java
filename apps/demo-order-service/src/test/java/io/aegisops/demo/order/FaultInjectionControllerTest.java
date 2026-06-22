package io.aegisops.demo.order;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FaultInjectionControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private FaultModeService faultModeService;

  @AfterEach
  void tearDown() {
    faultModeService.reset();
  }

  @Test
  void shouldReturnDefaultState() throws Exception {
    mvc.perform(get("/demo/fault/state"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slowApiEnabled", equalTo(false)))
        .andExpect(jsonPath("$.cpuHighEnabled", equalTo(false)))
        .andExpect(jsonPath("$.healthy", equalTo(true)))
        .andExpect(jsonPath("$.errorLogEnabled", equalTo(false)));
  }

  @Test
  void shouldApplyPartialFaultMode() throws Exception {
    mvc.perform(
            post("/demo/fault/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "slowApiEnabled": true,
                      "slowApiDelayMs": 1200,
                      "healthy": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slowApiEnabled", equalTo(true)))
        .andExpect(jsonPath("$.slowApiDelayMs", equalTo(1200)))
        .andExpect(jsonPath("$.healthy", equalTo(false)))
        .andExpect(jsonPath("$.cpuHighEnabled", equalTo(false)));
  }

  @Test
  void shouldIgnoreEmptyFaultModeBody() throws Exception {
    mvc.perform(
            post("/demo/fault/apply").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slowApiEnabled", equalTo(false)))
        .andExpect(jsonPath("$.cpuHighEnabled", equalTo(false)))
        .andExpect(jsonPath("$.healthy", equalTo(true)));
  }

  @Test
  void shouldIgnoreMissingFaultModeBody() throws Exception {
    mvc.perform(post("/demo/fault/apply").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slowApiEnabled", equalTo(false)))
        .andExpect(jsonPath("$.healthy", equalTo(true)));
  }

  @Test
  void shouldEnableIncidentFaultMode() throws Exception {
    mvc.perform(post("/demo/fault/incident"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slowApiEnabled", equalTo(true)))
        .andExpect(jsonPath("$.slowApiDelayMs", equalTo(2500)))
        .andExpect(jsonPath("$.cpuHighEnabled", equalTo(true)))
        .andExpect(jsonPath("$.cpuWorkers", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.healthy", equalTo(false)))
        .andExpect(jsonPath("$.errorLogEnabled", equalTo(true)))
        .andExpect(jsonPath("$.errorCount", equalTo(5)));
  }

  @Test
  void shouldRecoverFaultMode() throws Exception {
    mvc.perform(post("/demo/fault/incident")).andExpect(status().isOk());

    mvc.perform(post("/demo/fault/recover"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slowApiEnabled", equalTo(false)))
        .andExpect(jsonPath("$.cpuHighEnabled", equalTo(false)))
        .andExpect(jsonPath("$.healthy", equalTo(true)))
        .andExpect(jsonPath("$.errorLogEnabled", equalTo(false)));
  }

  @Test
  void shouldResetFaultModeAndErrorCount() throws Exception {
    mvc.perform(post("/demo/fault/incident")).andExpect(status().isOk());

    mvc.perform(post("/demo/fault/reset"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorCount", equalTo(0)))
        .andExpect(jsonPath("$.healthy", equalTo(true)));
  }
}
