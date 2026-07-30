package io.aegisops.ai.client;

import org.springframework.http.HttpHeaders;

@FunctionalInterface
public interface AgentCredentialProvider {
  void apply(HttpHeaders headers);
}
