package io.aegisops.worker;

import io.aegisops.common.runtime.RuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(RuntimeProperties.class)
@SpringBootApplication(scanBasePackages = "io.aegisops")
public class AiOpsWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsWorkerApplication.class, args);
  }
}
