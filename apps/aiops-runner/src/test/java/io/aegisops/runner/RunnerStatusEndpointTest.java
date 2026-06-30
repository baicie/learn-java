package io.aegisops.runner;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.runtime.RuntimePhase;
import io.aegisops.common.runtime.RuntimeProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Endpoint contract guard for PR2.
 *
 * <p>Asserts that {@link RunnerController#status()} reports the configured {@link RuntimePhase}
 * under its stable wire label (e.g. {@code "phase5"}). PR2 (acf856e) introduced this field; this
 * test prevents a future refactor from silently dropping it and misleading the health probe and the
 * future Phase Discipline guard.
 */
class RunnerStatusEndpointTest {

  @Test
  void statusEndpointReportsConfiguredPhase5() {
    RuntimeProperties properties = new RuntimeProperties(RuntimePhase.PHASE_5);
    RunnerController controller = new RunnerController(properties);

    ApiResponse<Map<String, String>> response = controller.status();

    assertThat(response.success()).isTrue();
    assertThat(response.data()).containsEntry("phase", "phase5");
    assertThat(response.data()).containsEntry("status", "idle");
  }

  @Test
  void statusEndpointReportsConfiguredPhase0WhenExplicitlyDefaulted() {
    RuntimeProperties properties = new RuntimeProperties(RuntimePhase.PHASE_0);
    RunnerController controller = new RunnerController(properties);

    ApiResponse<Map<String, String>> response = controller.status();

    assertThat(response.data()).containsEntry("phase", "phase0");
  }

  @Test
  void statusEndpointFallsBackToPhase0WhenPropertiesDeclaresNullPhase() {
    RuntimeProperties properties = new RuntimeProperties(null);
    RunnerController controller = new RunnerController(properties);

    ApiResponse<Map<String, String>> response = controller.status();

    assertThat(response.data()).containsEntry("phase", "phase0");
  }
}
