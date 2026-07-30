package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

public final class StaticInternalServiceAuthenticator implements InternalServiceAuthenticator {
  private final AiopsSecurityProperties properties;

  public StaticInternalServiceAuthenticator(AiopsSecurityProperties properties) {
    this.properties = properties;
  }

  @Override
  public InternalServicePrincipal authenticate(HttpServletRequest request) {
    String actualToken = request.getHeader(SecurityConstants.HEADER_INTERNAL_AGENT_TOKEN);
    if (!ConstantTimeTokenMatcher.matches(properties.getInternalAgentToken(), actualToken)) {
      throw new InternalServiceAuthenticationException("Invalid internal agent token");
    }
    return new InternalServicePrincipal("static:aiops-agent", Set.of("internal:agent"));
  }
}
