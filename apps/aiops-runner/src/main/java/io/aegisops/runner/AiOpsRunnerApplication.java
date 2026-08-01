package io.aegisops.runner;

import io.aegisops.common.runtime.RuntimeProperties;
import io.aegisops.execution.ExecutionProperties;
import io.aegisops.runner.executor.ansible.AnsibleRunnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@EnableScheduling
@EnableConfigurationProperties({
  RuntimeProperties.class,
  RunnerProperties.class,
  ExecutionProperties.class,
  AnsibleRunnerProperties.class
})
@SpringBootApplication
@ComponentScan(
    basePackages = "io.aegisops",
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ANNOTATION,
            classes = {RestController.class, Controller.class}))
@Import(RunnerController.class)
public class AiOpsRunnerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsRunnerApplication.class, args);
  }
}
