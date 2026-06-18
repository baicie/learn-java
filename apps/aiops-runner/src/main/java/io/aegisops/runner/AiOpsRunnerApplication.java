package io.aegisops.runner;

import io.aegisops.execution.ExecutionProperties;
import io.aegisops.runner.executor.ansible.AnsibleRunnerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({
  RunnerProperties.class,
  ExecutionProperties.class,
  AnsibleRunnerProperties.class
})
@SpringBootApplication(scanBasePackages = "io.aegisops")
public class AiOpsRunnerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsRunnerApplication.class, args);
  }
}
