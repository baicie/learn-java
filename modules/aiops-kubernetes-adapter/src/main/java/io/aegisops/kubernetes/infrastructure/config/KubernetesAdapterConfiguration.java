package io.aegisops.kubernetes.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.kubernetes.infrastructure.adapter.HttpKubernetesInventoryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesAdapterConfiguration {
  @Bean
  KubernetesInventoryClientFactory kubernetesInventoryClientFactory(ObjectMapper objectMapper) {
    return config -> new HttpKubernetesInventoryClient(config, objectMapper);
  }
}
