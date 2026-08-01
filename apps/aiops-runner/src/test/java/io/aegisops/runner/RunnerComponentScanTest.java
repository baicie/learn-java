package io.aegisops.runner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    classes = AiOpsRunnerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
    properties = {
      "aiops.runner.enabled=false",
      "spring.flyway.enabled=false",
      "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/aegisops",
      "spring.datasource.username=test",
      "spring.datasource.password=test",
      "spring.datasource.hikari.initialization-fail-timeout=-1",
      "spring.datasource.hikari.connection-timeout=250",
      "spring.jooq.sql-dialect=POSTGRES"
    })
class RunnerComponentScanTest {

  private final RequestMappingHandlerMapping handlerMapping;

  @Autowired
  RunnerComponentScanTest(
      @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
    this.handlerMapping = handlerMapping;
  }

  @Test
  void runnerExplicitlyExcludesSharedBusinessControllersAndImportsItsStatusController() {
    ComponentScan scan =
        AnnotatedElementUtils.findMergedAnnotation(
            AiOpsRunnerApplication.class, ComponentScan.class);

    assertThat(scan).isNotNull();
    assertThat(
            Arrays.stream(scan.excludeFilters()).flatMap(filter -> Arrays.stream(filter.classes())))
        .contains(RestController.class, Controller.class);

    Import imported = AiOpsRunnerApplication.class.getDeclaredAnnotation(Import.class);
    assertThat(imported).isNotNull();
    assertThat(imported.value()).contains(RunnerController.class);
  }

  @Test
  void runnerOnlyPublishesItsStatusEndpoint() {
    Set<String> aegisopsPaths = new TreeSet<>();

    for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
      HandlerMethod handler = entry.getValue();
      if (handler.getBeanType().getPackageName().startsWith("io.aegisops")) {
        aegisopsPaths.addAll(paths(entry.getKey()));
      }
    }

    assertThat(aegisopsPaths).containsExactly("/internal/runner/status");
    assertThat(aegisopsPaths)
        .noneMatch(path -> path.equals("/api") || path.startsWith("/api/"))
        .noneMatch(path -> path.equals("/internal/agent") || path.startsWith("/internal/agent/"));
  }

  private static Set<String> paths(RequestMappingInfo mapping) {
    return mapping.getPatternValues();
  }
}
