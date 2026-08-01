package io.aegisops.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.AgentCredentialProvider;
import io.aegisops.ai.client.AgentServiceAuthConfiguration;
import io.aegisops.ai.client.DiagnosisGrantProvider;
import io.aegisops.ai.client.OAuth2ClientCredentialsProvider;
import io.aegisops.ai.client.SignedDiagnosisGrantProvider;
import io.aegisops.security.AiopsSecurityConfiguration;
import io.aegisops.security.InternalServiceAuthenticator;
import io.aegisops.security.JwtInternalServiceAuthenticator;
import io.aegisops.security.RateLimitDecision;
import io.aegisops.security.RateLimitService;
import io.aegisops.security.TenantSecurityAuditService;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;

class WorkerServiceAuthConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(
              context ->
                  context
                      .getEnvironment()
                      .getPropertySources()
                      .addLast(
                          new PropertiesPropertySource(
                              "workerApplication", workerApplicationProperties())))
          .withPropertyValues("aiops.outbox.enabled=false", "aiops.zabbix-sync.enabled=false")
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(
              TenantSecurityAuditService.class,
              () -> new TenantSecurityAuditService(command -> {}, new ObjectMapper()))
          .withBean(
              RateLimitService.class,
              () -> (key, limit, window) -> RateLimitDecision.allowed(limit, 0))
          .withUserConfiguration(ServiceAuthTestConfiguration.class);

  @Test
  void providesInboundOauth2VerifierConfigurationRequiredBySharedSecurityContext() {
    Properties properties = workerApplicationProperties();

    assertThat(properties).isNotNull();
    assertThat(properties.getProperty("aiops.security.internal-agent-jwt-issuer-uri")).isNotBlank();
    assertThat(properties.getProperty("aiops.security.internal-agent-jwt-jwk-set-uri"))
        .isNotBlank();
    assertThat(properties.getProperty("aiops.agent.auth.scope"))
        .contains("agent:diagnose", "agent:work-record");
  }

  @Test
  void startsMinimumServiceAuthContextFromWorkerConfiguration() {
    contextRunner
        .withPropertyValues("aiops.agent.enabled=true", "aiops.internal-agent-api.enabled=true")
        .run(
            context -> {
              assertThat(context.getStartupFailure()).isNull();
              assertThat(context.getBean(InternalServiceAuthenticator.class))
                  .isInstanceOf(JwtInternalServiceAuthenticator.class);
              assertThat(context.getBean(AgentCredentialProvider.class))
                  .isInstanceOf(OAuth2ClientCredentialsProvider.class);
              assertThat(context.getBean(DiagnosisGrantProvider.class))
                  .isInstanceOf(SignedDiagnosisGrantProvider.class);
              assertThat(
                      context.getEnvironment().getProperty("aiops.outbox.enabled", Boolean.class))
                  .isFalse();
              assertThat(
                      context
                          .getEnvironment()
                          .getProperty("aiops.zabbix-sync.enabled", Boolean.class))
                  .isFalse();
            });
  }

  @Test
  void doesNotCreateServiceAuthBeansWhenAgentCapabilitiesAreDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(InternalServiceAuthenticator.class);
          assertThat(context).doesNotHaveBean(AgentCredentialProvider.class);
          assertThat(context).doesNotHaveBean(DiagnosisGrantProvider.class);
        });
  }

  private static Properties workerApplicationProperties() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("application.yml"));
    return yaml.getObject();
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    AiopsSecurityConfiguration.class,
    AgentServiceAuthConfiguration.class,
    SignedDiagnosisGrantProvider.class
  })
  static class ServiceAuthTestConfiguration {}
}
