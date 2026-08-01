package io.aegisops.security;

import com.nimbusds.jose.RemoteKeySourceException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public final class JwtInternalServiceAuthenticator implements InternalServiceAuthenticator {
  private static final Duration MAX_SERVICE_TOKEN_LIFETIME = Duration.ofMinutes(5);
  private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofSeconds(30);

  private final AiopsSecurityProperties properties;
  private final JwtDecoder decoder;

  public JwtInternalServiceAuthenticator(AiopsSecurityProperties properties, JwtDecoder decoder) {
    this.properties = properties;
    this.decoder = decoder;
  }

  @Override
  public InternalServicePrincipal authenticate(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization == null
        || !authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
        || authorization.substring("Bearer ".length()).isBlank()) {
      throw new InternalServiceAuthenticationException("Bearer service token is required");
    }

    try {
      Jwt jwt = decoder.decode(authorization.substring("Bearer ".length()).trim());
      Instant expiresAt = jwt.getExpiresAt();
      if (expiresAt == null) {
        throw new InternalServiceAuthenticationException("Service token expiration is required");
      }
      Instant issuedAt = jwt.getIssuedAt();
      if (issuedAt == null) {
        throw new InternalServiceAuthenticationException("Service token issued-at is required");
      }
      if (issuedAt.isAfter(Instant.now().plus(ALLOWED_CLOCK_SKEW))) {
        throw new InternalServiceAuthenticationException(
            "Service token issued-at is in the future");
      }
      Duration tokenLifetime = Duration.between(issuedAt, expiresAt);
      if (tokenLifetime.isZero()
          || tokenLifetime.isNegative()
          || tokenLifetime.compareTo(MAX_SERVICE_TOKEN_LIFETIME) > 0) {
        throw new InternalServiceAuthenticationException("Invalid service token lifetime");
      }
      List<String> audience = jwt.getAudience();
      if (audience == null || !audience.contains(properties.getInternalAgentJwtAudience())) {
        throw new InternalServiceAuthenticationException("Invalid service token audience");
      }

      if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
        throw new InternalServiceAuthenticationException("Service token subject is required");
      }
      Set<String> scopes = extractScopes(jwt);
      InternalServicePrincipal principal = new InternalServicePrincipal(jwt.getSubject(), scopes);
      request.setAttribute(SecurityConstants.REQUEST_ATTRIBUTE_SERVICE_PRINCIPAL, principal);
      String requiredScope = InternalAgentScopePolicy.requiredScope(request);
      if (!scopes.contains(requiredScope)) {
        throw new InternalServiceAuthorizationException(
            "Service token is missing required scope: " + requiredScope);
      }
      return principal;
    } catch (InternalServiceAuthenticationException exception) {
      throw exception;
    } catch (JwtException exception) {
      if (hasCause(exception, RemoteKeySourceException.class)) {
        throw new InternalServiceAuthenticationUnavailableException(
            "Service authentication is temporarily unavailable", exception);
      }
      throw new InternalServiceAuthenticationException("Invalid service token", exception);
    }
  }

  private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
    Throwable cause = exception;
    while (cause != null) {
      if (type.isInstance(cause)) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
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
