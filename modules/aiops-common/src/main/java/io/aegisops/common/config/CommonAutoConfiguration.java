package io.aegisops.common.config;

import io.aegisops.common.time.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonAutoConfiguration {
    @Bean
    public TimeProvider timeProvider() {
        return new TimeProvider();
    }
}
