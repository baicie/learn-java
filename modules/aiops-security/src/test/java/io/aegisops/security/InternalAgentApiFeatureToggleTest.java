package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
  void doesNotCreateInternalMtlsSecurityOrProbeControllerByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(InternalServiceAuthenticator.class);
          assertThat(context).doesNotHaveBean(InternalAgentAuthFilter.class);
          assertThat(context).doesNotHaveBean(InternalServiceAuthProbeController.class);
        });
  }

  @Test
  void createsMtlsAndEd25519GrantSecurityWhenExplicitlyEnabled(@TempDir Path tempDir)
      throws Exception {
    Path publicKeyFile = tempDir.resolve("task-grant-public.pem");
    byte[] encoded =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    Files.writeString(
        publicKeyFile, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");

    contextRunner
        .withPropertyValues(
            "aiops.internal-agent-api.enabled=true",
            "aiops.security.diagnosis-grant-key-id=task-grant-v1",
            "aiops.security.diagnosis-grant-public-key-file=" + publicKeyFile)
        .run(
            context -> {
              assertThat(context).hasSingleBean(InternalServiceAuthenticator.class);
              assertThat(context.getBean(InternalServiceAuthenticator.class))
                  .isInstanceOf(MtlsInternalServiceAuthenticator.class);
              assertThat(context).hasSingleBean(DiagnosisGrantVerificationKeys.class);
              assertThat(context).hasSingleBean(InternalAgentAuthFilter.class);
              assertThat(context).hasSingleBean(InternalServiceAuthProbeController.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({AiopsSecurityConfiguration.class, InternalServiceAuthProbeController.class})
  static class InternalAgentApiTestConfiguration {}
}
