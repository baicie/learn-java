package io.aegisops.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public final class JwtInternalServiceAuthenticator implements InternalServiceAuthenticator {
  private final AiopsSecurityProperties properties;
  private final JwtDecoder decoder;

  public JwtInternalServiceAuthenticator(AiopsSecurityProperties properties, JwtDecoder decoder) {
    this.properties = properties;
    this.decoder = decoder;
  }

  @Override
  public InternalServicePrincipal authenticate(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new InternalServiceAuthenticationException("Bearer service token is required");
    }

    try {
      Jwt jwt = decoder.decode(authorization.substring("Bearer ".length()).trim());
      if (!jwt.getAudience().contains(properties.getInternalAgentJwtAudience())) {
        throw new InternalServiceAuthenticationException("Invalid service token audience");
      }

      Set<String> scopes = extractScopes(jwt);
      String requiredScope = InternalAgentScopePolicy.requiredScope(request);
      if (!scopes.contains(requiredScope)) {
        throw new InternalServiceAuthenticationException(
            "Service token is missing required scope: " + requiredScope);
      }
      if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
        throw new InternalServiceAuthenticationException("Service token subject is required");
      }
      return new InternalServicePrincipal(jwt.getSubject(), scopes);
    } catch (InternalServiceAuthenticationException exception) {
      throw exception;
    } catch (JwtException exception) {
      throw new InternalServiceAuthenticationException("Invalid service token", exception);
    }
  }

  private Set<String> extractScopes(Jwt jwt) {
    Set<String> scopes = new HashSet<>();
    String scope = jwt.getClaimAsString("scope");
    if (scope != null && !scope.isBlank()) {
      scopes.addAll(Arrays.asList(scope.trim().split("\\s+")));
    }
    List<String> scp = jwt.getClaimAsStringList("scp");
    if (scp != null) {
      scopes.addAll(scp);
    }
    return Set.copyOf(scopes);
  }
}
