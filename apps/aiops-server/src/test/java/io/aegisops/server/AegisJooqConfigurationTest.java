package io.aegisops.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.persistence.AegisJooqConfiguration;
import org.jooq.impl.DefaultConfiguration;
import org.junit.jupiter.api.Test;

class AegisJooqConfigurationTest {
  @Test
  void disablesSchemaRenderingForPostgresRuntimeQueries() {
    var configuration = new DefaultConfiguration();

    new AegisJooqConfiguration().jooqConfigurationCustomizer().customize(configuration);

    assertThat(configuration.settings().isRenderSchema()).isFalse();
  }
}
