package io.aegisops.ai.client;

import org.springframework.http.HttpHeaders;

public final class StaticAgentCredentialProvider implements AgentCredentialProvider {
  private final AgentClientProperties properties;

  public StaticAgentCredentialProvider(AgentClientProperties properties) {
    this.properties = properties;
  }

  @Override
  public void apply(HttpHeaders headers) {
    headers.set(AgentContract.INTERNAL_TOKEN_HEADER, properties.normalizedInternalToken());
  }
}
