package io.aegisops.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.aegisops")
public class AiOpsWorkerApplication {
  public static void main(String[] args) {
    SpringApplication.run(AiOpsWorkerApplication.class, args);
  }
}
