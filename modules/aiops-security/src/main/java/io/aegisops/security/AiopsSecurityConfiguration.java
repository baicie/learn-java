package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
@EnableConfigurationProperties({AiopsSecurityProperties.class, AiopsQuotaProperties.class})
public class AiopsSecurityConfiguration {
  @Bean
  SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) {
    return new SecurityErrorResponseWriter(objectMapper);
  }

  @Bean
  TenantRequiredFilter tenantRequiredFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new TenantRequiredFilter(properties, responseWriter, auditService);
  }

  @Bean
  InternalServiceAuthenticator internalServiceAuthenticator(AiopsSecurityProperties properties) {
    properties.validateInternalServiceAuthentication();

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(properties.getInternalAgentJwtJwkSetUri()).build();
    JwtTimestampValidator timestampValidator = strictServiceTokenTimestampValidator();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(
            JwtValidators.createDefaultWithIssuer(properties.getInternalAgentJwtIssuerUri()),
            timestampValidator));
    return new JwtInternalServiceAuthenticator(properties, decoder);
  }

  static JwtTimestampValidator strictServiceTokenTimestampValidator() {
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
    timestampValidator.setAllowEmptyExpiryClaim(false);
    return timestampValidator;
  }

  @Bean
  InternalAgentAuthFilter internalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService,
      InternalServiceAuthenticator authenticator) {
    return new InternalAgentAuthFilter(
        properties, responseWriter, auditService, authenticator, Clock.systemUTC());
  }

  @Bean
  TenantRateLimitFilter tenantRateLimitFilter(
      AiopsQuotaProperties quotaProperties,
      RateLimitService rateLimitService,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new TenantRateLimitFilter(
        quotaProperties, rateLimitService, responseWriter, auditService);
  }

  /**
   * These filters must run inside Spring Security chain, not as standalone servlet filters.
   *
   * <p>TenantRequiredFilter depends on JwtAuthenticationFilter being able to set TenantContext for
   * public /api/** requests.
   */
  @Bean
  FilterRegistrationBean<TenantRequiredFilter> disableTenantRequiredFilterAutoRegistration(
      TenantRequiredFilter filter) {
    FilterRegistrationBean<TenantRequiredFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  FilterRegistrationBean<InternalAgentAuthFilter> disableInternalAgentAuthFilterAutoRegistration(
      InternalAgentAuthFilter filter) {
    FilterRegistrationBean<InternalAgentAuthFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  FilterRegistrationBean<TenantRateLimitFilter> disableTenantRateLimitFilterAutoRegistration(
      TenantRateLimitFilter filter) {
    FilterRegistrationBean<TenantRateLimitFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
