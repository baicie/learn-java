package io.aegisops.workrecord.application.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.workrecord.application.port.WorkRecordDictionaryPort;
import io.aegisops.workrecord.domain.rule.WorkRecordValueValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkRecordDomainConfig {
  @Bean
  WorkRecordValueValidator workRecordValueValidator(
      ObjectMapper objectMapper, WorkRecordDictionaryPort dictionaryPort) {
    return new WorkRecordValueValidator(objectMapper, dictionaryPort);
  }
}
