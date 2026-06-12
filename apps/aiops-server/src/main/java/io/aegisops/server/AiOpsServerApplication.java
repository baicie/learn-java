package io.aegisops.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.aegisops")
public class AiOpsServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiOpsServerApplication.class, args);
    }
}
