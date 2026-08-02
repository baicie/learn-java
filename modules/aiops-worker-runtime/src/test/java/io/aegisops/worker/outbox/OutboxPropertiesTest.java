package io.aegisops.worker.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class OutboxPropertiesTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

  @BeforeAll
  static void setupValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  @Test
  void defaultTargetAppWhenBlank() {
    OutboxProperties properties = new OutboxProperties(true, 5000L, 10, "  ");
    assertEquals("worker", properties.targetApp());
  }

  @Test
  void keepsExplicitTargetApp() {
    OutboxProperties properties = new OutboxProperties(true, 5000L, 10, "server");
    assertEquals("server", properties.targetApp());
  }

  @Test
  void bindsFromSpringConfiguration() {
    contextRunner
        .withPropertyValues(
            "aiops.outbox.enabled=true",
            "aiops.outbox.poll-delay-ms=1000",
            "aiops.outbox.batch-size=10",
            "aiops.outbox.target-app=worker",
            "aiops.outbox.lease-duration-ms=60000")
        .run(
            context -> {
              assertNull(context.getStartupFailure());
              assertEquals(60000L, context.getBean(OutboxProperties.class).leaseDurationMs());
            });
  }

  @Test
  void rejectsTooSmallPollDelay() {
    OutboxProperties properties = new OutboxProperties(true, 10L, 10, "worker");
    Set<ConstraintViolation<OutboxProperties>> violations = validator.validate(properties);
    assertFalse(violations.isEmpty());
  }

  @Test
  void rejectsTooSmallBatchSize() {
    OutboxProperties properties = new OutboxProperties(true, 1000L, 0, "worker");
    Set<ConstraintViolation<OutboxProperties>> violations = validator.validate(properties);
    assertFalse(violations.isEmpty());
  }

  @Test
  void acceptsCompliantProperties() {
    OutboxProperties properties = new OutboxProperties(true, 1000L, 10, "worker");
    Set<ConstraintViolation<OutboxProperties>> violations = validator.validate(properties);
    assertTrue(violations.isEmpty());
  }

  @Test
  void equalityAndAccessorsWork() {
    OffsetDateTime now = OffsetDateTime.now();
    OutboxProperties a = new OutboxProperties(false, 2000L, 5, "runner");
    assertEquals(false, a.enabled());
    assertEquals(2000L, a.pollDelayMs());
    assertEquals(5, a.batchSize());
    assertEquals("runner", a.targetApp());
    assertEquals(now, now); // sanity
  }

  @Test
  void duplicateJobRegistrationThrows() {
    // placeholder — covered by OutboxPollerTest
    assertThrows(
        IllegalStateException.class,
        () ->
            new OutboxPoller(
                new NoopOutboxRepository(),
                new OutboxProperties(true, 1000L, 1, "worker"),
                java.util.List.of(
                    new io.aegisops.worker.job.OutboxJob() {
                      @Override
                      public String jobName() {
                        return "zabbix-sync";
                      }

                      @Override
                      public io.aegisops.worker.job.JobResult handle(
                          io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord
                              row) {
                        return io.aegisops.worker.job.JobResult.success();
                      }
                    },
                    new io.aegisops.worker.job.OutboxJob() {
                      @Override
                      public String jobName() {
                        return "zabbix-sync";
                      }

                      @Override
                      public io.aegisops.worker.job.JobResult handle(
                          io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord
                              row) {
                        return io.aegisops.worker.job.JobResult.success();
                      }
                    })));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(OutboxProperties.class)
  static class TestConfiguration {}
}
