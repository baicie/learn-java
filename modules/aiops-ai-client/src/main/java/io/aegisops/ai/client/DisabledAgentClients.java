package io.aegisops.ai.client;

import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.common.exception.AppException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "aiops.agent",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledAgentClients {
  @Bean
  AiAgentClient disabledAiAgentClient() {
    return request -> {
      throw disabled();
    };
  }

  @Bean
  WorkRecordAiClient disabledWorkRecordAiClient() {
    return request -> {
      throw disabled();
    };
  }

  private static AppException disabled() {
    return new AppException("AI_AGENT_DISABLED", 503, "AI Agent is disabled for this deployment");
  }
}
