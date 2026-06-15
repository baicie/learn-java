package io.aegisops.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.aegisops")
public class AiOpsRunnerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsRunnerApplication.class, args);
  }
}
