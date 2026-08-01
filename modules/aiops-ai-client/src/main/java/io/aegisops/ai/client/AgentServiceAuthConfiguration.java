package io.aegisops.ai.client;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(AgentServiceAuthProperties.class)
public class AgentServiceAuthConfiguration {
  @Bean
  AgentCredentialProvider agentCredentialProvider(AgentServiceAuthProperties authProperties) {
    authProperties.validate();
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(3000);
    requestFactory.setReadTimeout(5000);
    return new OAuth2ClientCredentialsProvider(
        authProperties, new RestTemplate(requestFactory), Clock.systemUTC());
  }
}
