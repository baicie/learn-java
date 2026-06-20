package io.aegisops.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    AiopsSecurityProperties.class,
    AiopsQuotaProperties.class
})
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
    return new TenantRateLimitFilter(
        quotaProperties,
        rateLimiter,
        responseWriter,
        auditService);
  }
}
