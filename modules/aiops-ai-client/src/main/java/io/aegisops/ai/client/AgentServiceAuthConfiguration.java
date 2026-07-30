package io.aegisops.ai.client;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(AgentServiceAuthProperties.class)
public class AgentServiceAuthConfiguration {
  @Bean
  AgentCredentialProvider agentCredentialProvider(
      AgentServiceAuthProperties authProperties, AgentClientProperties clientProperties) {
    if (authProperties.oauth2Enabled()) {
      return new OAuth2ClientCredentialsProvider(
          authProperties, new RestTemplate(), Clock.systemUTC());
    }
    if (!"static".equalsIgnoreCase(authProperties.getMode())) {
      throw new IllegalStateException(
          "Unsupported aiops.agent.auth.mode: " + authProperties.getMode());
    }
    return new StaticAgentCredentialProvider(clientProperties);
  }
}
