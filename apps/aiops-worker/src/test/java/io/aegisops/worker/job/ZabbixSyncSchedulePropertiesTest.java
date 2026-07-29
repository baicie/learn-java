package io.aegisops.worker.job;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ZabbixSyncSchedulePropertiesTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @Test
  void bindsWorkerSchedulingConfiguration() {
    contextRunner
        .withPropertyValues(
            "aiops.zabbix-sync.enabled=true",
            "aiops.zabbix-sync.poll-delay-ms=5000",
            "aiops.zabbix-sync.cadence-ms=60000",
            "aiops.zabbix-sync.batch-size=25")
        .run(
            context -> {
              assertThat(context.getStartupFailure()).isNull();
              ZabbixSyncScheduleProperties properties =
                  context.getBean(ZabbixSyncScheduleProperties.class);
              assertThat(properties.enabled()).isTrue();
              assertThat(properties.pollDelayMs()).isEqualTo(5000L);
              assertThat(properties.cadenceMs()).isEqualTo(60000L);
              assertThat(properties.batchSize()).isEqualTo(25);
            });
  }

  @Test
  void rejectsNonPositiveCadenceAndBatchSize() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      var properties = new ZabbixSyncScheduleProperties(true, 5000L, 0L, 0);

      assertThat(validator.validate(properties)).hasSize(2);
    }
  }

  @Test
  void rejectsInvalidBoundConfigurationAtStartup() {
    contextRunner
        .withPropertyValues(
            "aiops.zabbix-sync.enabled=true",
            "aiops.zabbix-sync.poll-delay-ms=99",
            "aiops.zabbix-sync.cadence-ms=0",
            "aiops.zabbix-sync.batch-size=0")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ZabbixSyncScheduleProperties.class)
  static class TestConfiguration {}
}
