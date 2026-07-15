package io.aegisops.worker;

import io.aegisops.common.runtime.RuntimeProperties;
import io.aegisops.worker.outbox.OutboxProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@EnableConfigurationProperties({RuntimeProperties.class, OutboxProperties.class})
@SpringBootApplication
@ComponentScan(
    basePackages = "io.aegisops",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = {RestController.class, Controller.class}))
public class AiOpsWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsWorkerApplication.class, args);
  }
}
