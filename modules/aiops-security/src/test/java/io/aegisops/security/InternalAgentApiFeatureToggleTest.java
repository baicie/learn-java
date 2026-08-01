package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class InternalAgentApiFeatureToggleTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(
              TenantSecurityAuditService.class,
              () -> new TenantSecurityAuditService(command -> {}, new ObjectMapper()))
          .withBean(
              RateLimitService.class,
              () -> (key, limit, window) -> RateLimitDecision.allowed(limit, 0))
          .withUserConfiguration(InternalAgentApiTestConfiguration.class);

  @Test
  void doesNotCreateJwksSecurityOrProbeControllerByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(InternalServiceAuthenticator.class);
          assertThat(context).doesNotHaveBean(InternalAgentAuthFilter.class);
          assertThat(context).doesNotHaveBean(InternalServiceAuthProbeController.class);
        });
  }

  @Test
  void createsJwksSecurityAndProbeControllerWhenExplicitlyEnabled() {
    contextRunner
        .withPropertyValues(
            "aiops.internal-agent-api.enabled=true",
            "aiops.security.internal-agent-jwt-issuer-uri=http://idp/realms/aegisops",
            "aiops.security.internal-agent-jwt-jwk-set-uri=http://idp/certs",
            "aiops.security.internal-agent-jwt-audience=aegisops-internal-api",
            "aiops.security.diagnosis-grant-secret=test-diagnosis-grant-secret-change-me")
        .run(
            context -> {
              assertThat(context).hasSingleBean(InternalServiceAuthenticator.class);
              assertThat(context).hasSingleBean(InternalAgentAuthFilter.class);
              assertThat(context).hasSingleBean(InternalServiceAuthProbeController.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({AiopsSecurityConfiguration.class, InternalServiceAuthProbeController.class})
  static class InternalAgentApiTestConfiguration {}
}
