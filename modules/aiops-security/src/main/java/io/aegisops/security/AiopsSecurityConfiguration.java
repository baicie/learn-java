package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
  InternalAgentAuthFilter internalAgentAuthFilter(
      AiopsSecurityProperties properties,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new InternalAgentAuthFilter(properties, responseWriter, auditService);
  }

  @Bean
  TenantRateLimitFilter tenantRateLimitFilter(
      AiopsQuotaProperties quotaProperties,
      InMemoryTenantRateLimiter rateLimiter,
      SecurityErrorResponseWriter responseWriter,
      TenantSecurityAuditService auditService) {
    return new TenantRateLimitFilter(quotaProperties, rateLimiter, responseWriter, auditService);
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
