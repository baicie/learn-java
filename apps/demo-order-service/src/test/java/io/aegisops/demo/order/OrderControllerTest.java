package io.aegisops.demo.order;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class OrderControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private FaultModeService faultModeService;

  @AfterEach
  void tearDown() {
    faultModeService.reset();
  }

  @Test
  void shouldReturnHealthUpByDefault() throws Exception {
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", equalTo("UP")))
        .andExpect(jsonPath("$.service", equalTo("order-service")));
  }

  @Test
  void shouldReturnHealthDownWhenFaultEnabled() throws Exception {
    faultModeService.apply(new FaultMode(null, null, null, null, false, null));

    mvc.perform(get("/health"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status", equalTo("DOWN")));
  }

  @Test
  void shouldCreateOrderWhenHealthy() throws Exception {
    mvc.perform(
            post("/api/order/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "skuId": "demo-sku",
                      "quantity": 1
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId", notNullValue()))
        .andExpect(jsonPath("$.status", equalTo("CREATED")));
  }

  @Test
  void shouldFailOrderWhenUnhealthy() throws Exception {
    faultModeService.apply(new FaultMode(null, null, null, null, false, null));

    mvc.perform(
            post("/api/order/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "skuId": "demo-sku",
                      "quantity": 1
                    }
                    """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status", equalTo("FAILED")));
  }

  @Test
  void shouldIncreaseErrorCountWhenErrorLogEnabled() throws Exception {
    faultModeService.apply(new FaultMode(null, null, null, null, null, true));

    mvc.perform(
            post("/api/order/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "skuId": "demo-sku",
                      "quantity": 1
                    }
                    """))
        .andExpect(status().isOk());

    mvc.perform(get("/demo/fault/state"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errorCount").value(6));
  }
}
