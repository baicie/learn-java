package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.runtime.RuntimePhase;
import io.aegisops.common.runtime.RuntimeProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Endpoint contract guard for PR2.
 *
 * <p>Asserts that {@link SystemController#status()} reports the configured {@link RuntimePhase}
 * under its stable wire label (e.g. {@code "phase5"}). PR2 (acf856e) introduced phase reporting on
 * Worker/Runner but Server's SystemController was not updated — this test closes the gap so that
 * health probes can identify the Server's MVP phase uniformly.
 */
class SystemControllerPhaseTest {

  @Test
  void statusEndpointReportsConfiguredPhase5() {
    RuntimeProperties properties = new RuntimeProperties(RuntimePhase.PHASE_5);
    SystemController controller = new SystemController(mockJdbc(), properties, "aiops-server");

    ApiResponse<Map<String, String>> response = controller.status();

    assertThat(response.success()).isTrue();
    assertThat(response.data()).containsEntry("phase", "phase5");
    assertThat(response.data()).containsEntry("app", "aiops-server");
  }

  @Test
  void statusEndpointReportsConfiguredPhase0WhenExplicitlyDefaulted() {
    RuntimeProperties properties = new RuntimeProperties(RuntimePhase.PHASE_0);
    SystemController controller = new SystemController(mockJdbc(), properties, "aiops-server");

    ApiResponse<Map<String, String>> response = controller.status();

    assertThat(response.data()).containsEntry("phase", "phase0");
  }

  @Test
  void statusEndpointFallsBackToPhase0WhenPropertiesDeclaresNullPhase() {
    RuntimeProperties properties = new RuntimeProperties(null);
    SystemController controller = new SystemController(mockJdbc(), properties, "aiops-server");

    ApiResponse<Map<String, String>> response = controller.status();

    assertThat(response.data()).containsEntry("phase", "phase0");
  }

  /**
   * {@link SystemController#status()} does not touch the {@link JdbcTemplate}, so a Mockito mock is
   * the cheapest way to satisfy the constructor without standing up a Spring context.
   */
  private static JdbcTemplate mockJdbc() {
    return org.mockito.Mockito.mock(JdbcTemplate.class);
  }
}
