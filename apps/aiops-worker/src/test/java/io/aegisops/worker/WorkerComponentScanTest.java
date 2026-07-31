package io.aegisops.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.NoOpTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class WorkerComponentScanTest {
  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withInitializer(
              context ->
                  context
                      .getEnvironment()
                      .getPropertySources()
                      .addLast(
                          new PropertiesPropertySource(
                              "workerApplication", workerApplicationProperties())))
          .withPropertyValues(
              "spring.main.lazy-initialization=true",
              "spring.flyway.enabled=false",
              "spring.sql.init.mode=never",
              "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/aegisops",
              "spring.datasource.username=test",
              "spring.datasource.password=test",
              "spring.datasource.hikari.initialization-fail-timeout=-1",
              "spring.datasource.hikari.connection-timeout=250",
              "spring.jooq.sql-dialect=POSTGRES",
              "aiops.outbox.enabled=false",
              "aiops.zabbix-sync.enabled=false")
          .withBean(
              "taskScheduler",
              TaskScheduler.class,
              NoOpTaskScheduler::new,
              beanDefinition -> beanDefinition.setPrimary(true))
          .withBean(
              NamedParameterJdbcTemplate.class,
              WorkerComponentScanTest::workRecordMetricsJdbcTemplate)
          .withUserConfiguration(AiOpsWorkerApplication.class);

  @Test
  void workerExplicitlyExcludesBusinessControllersAndImportsItsStatusController() {
    ComponentScan scan =
        AnnotatedElementUtils.findMergedAnnotation(
            AiOpsWorkerApplication.class, ComponentScan.class);

    assertThat(scan).isNotNull();
    assertThat(
            Arrays.stream(scan.excludeFilters()).flatMap(filter -> Arrays.stream(filter.classes())))
        .contains(RestController.class, Controller.class);

    Import imported = AiOpsWorkerApplication.class.getDeclaredAnnotation(Import.class);
    assertThat(imported).isNotNull();
    assertThat(imported.value()).contains(WorkerController.class);
  }

  @Test
  void workerScansSharedConfigurationProperties() {
    ConfigurationPropertiesScan scan =
        AnnotatedElementUtils.findMergedAnnotation(
            AiOpsWorkerApplication.class, ConfigurationPropertiesScan.class);

    assertThat(scan).isNotNull();
    assertThat(scan.basePackages()).contains("io.aegisops");
  }

  @Test
  void workerOnlyPublishesItsStatusEndpoint() {
    contextRunner.run(
        context -> {
          assertThat(context.getStartupFailure()).isNull();
          RequestMappingHandlerMapping handlerMapping =
              context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
          Set<String> aegisopsPaths = new TreeSet<>();

          for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (handler.getBeanType().getPackageName().startsWith("io.aegisops")) {
              aegisopsPaths.addAll(paths(entry.getKey()));
            }
          }

          assertThat(aegisopsPaths).containsExactly("/internal/worker/status");
          assertThat(aegisopsPaths)
              .noneMatch(path -> path.equals("/api") || path.startsWith("/api/"))
              .noneMatch(
                  path -> path.equals("/internal/agent") || path.startsWith("/internal/agent/"));
        });
  }

  private static Set<String> paths(RequestMappingInfo mapping) {
    return mapping.getPatternValues();
  }

  private static Properties workerApplicationProperties() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    return yaml.getObject();
  }

  private static NamedParameterJdbcTemplate workRecordMetricsJdbcTemplate() {
    return new NamedParameterJdbcTemplate(new JdbcTemplate()) {
      @Override
      public Map<String, Object> queryForMap(String sql, Map<String, ?> paramMap) {
        return Map.of("template_count", 0, "field_count", 0, "record_count", 0);
      }
    };
  }
}
