package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.evidence.MetricsEvidenceClient;
import io.aegisops.evidence.NoopMetricsEvidenceClient;
import io.aegisops.evidence.VictoriaMetricsEvidenceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MetricsEvidenceClientConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              NoopMetricsEvidenceClient.class, VictoriaMetricsEvidenceClient.class);

  @Test
  void providesNoopMetricsClientWhenVictoriaMetricsIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(MetricsEvidenceClient.class);
          assertThat(context).hasSingleBean(NoopMetricsEvidenceClient.class);
        });
  }
}
