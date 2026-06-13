package io.aegisops.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "io.aegisops")
@ConfigurationPropertiesScan(basePackages = "io.aegisops")
public class AiOpsServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiOpsServerApplication.class, args);
    }
}
