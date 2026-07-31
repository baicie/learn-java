package io.aegisops.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "io.aegisops")
@ComponentScan(
    basePackages = "io.aegisops",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = {RestController.class, Controller.class}))
@Import(WorkerController.class)
public class AiOpsWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsWorkerApplication.class, args);
  }
}
