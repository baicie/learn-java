package io.aegisops.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

class WorkerComponentScanTest {
  @Test
  void workerExplicitlyExcludesBusinessControllers() {
    ComponentScan scan =
        AnnotatedElementUtils.findMergedAnnotation(
            AiOpsWorkerApplication.class, ComponentScan.class);

    assertThat(scan).isNotNull();
    assertThat(
            Arrays.stream(scan.excludeFilters()).flatMap(filter -> Arrays.stream(filter.classes())))
        .contains(RestController.class, Controller.class);
  }
}
