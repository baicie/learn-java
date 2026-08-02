package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.workrecord.HttpWorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.common.exception.AppException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

class AgentFeatureToggleTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class, ObjectMapper::new)
          .withBean(RestClient.Builder.class, RestClient::builder)
          .withUserConfiguration(AgentClientTestConfiguration.class);

  @Test
  void usesFailClosedClientsWithoutOauthWhenAgentIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AiAgentClient.class);
          assertThat(context).hasSingleBean(WorkRecordAiClient.class);
          assertThat(context.getBeanNamesForType(AiAgentClient.class))
              .containsExactly("disabledAiAgentClient");
          assertThat(context.getBeanNamesForType(WorkRecordAiClient.class))
              .containsExactly("disabledWorkRecordAiClient");
          assertThatThrownBy(
                  () ->
                      context
                          .getBean(AiAgentClient.class)
                          .diagnose(
                              new AgentDiagnosisRequest(
                                  null,
                                  "tenant-1",
                                  "incident-1",
                                  null,
                                  List.of(),
                                  null,
                                  List.of(),
                                  List.of(),
                                  null,
                                  null,
                                  "diagnosis-1")))
              .isInstanceOf(AppException.class)
              .hasMessage("AI Agent is disabled for this deployment");
          assertThatThrownBy(
                  () ->
                      context
                          .getBean(WorkRecordAiClient.class)
                          .generate(
                              new WorkRecordGenerationRequest(
                                  null,
                                  "summary",
                                  "tenant-1",
                                  "record-1",
                                  "user-1",
                                  null,
                                  null,
                                  null,
                                  null,
                                  List.of(),
                                  Map.of(),
                                  null)))
              .isInstanceOf(AppException.class)
              .hasMessage("AI Agent is disabled for this deployment");
          assertThat(context).doesNotHaveBean("agentCredentialProvider");
          assertThat(context).doesNotHaveBean(DiagnosisGrantProvider.class);
          assertThat(context).doesNotHaveBean(HttpAiAgentClient.class);
          assertThat(context).doesNotHaveBean(HttpWorkRecordAiClient.class);
        });
  }

  @Test
  void createsMtlsHttpClientsAndEd25519GrantSignerOnlyWhenAgentIsEnabled(@TempDir Path tempDir)
      throws Exception {
    Path privateKeyFile = tempDir.resolve("task-grant-private.pem");
    byte[] encoded =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPrivate().getEncoded();
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    Files.writeString(
        privateKeyFile, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");

    SslBundles bundles = mock(SslBundles.class);
    SslBundle bundle = mock(SslBundle.class);
    when(bundles.getBundle("agent-client")).thenReturn(bundle);
    when(bundle.createSslContext()).thenReturn(SSLContext.getDefault());

    contextRunner
        .withBean(SslBundles.class, () -> bundles)
        .withPropertyValues(
            "aiops.agent.enabled=true",
            "aiops.agent.base-url=https://agent:9008",
            "aiops.agent.ssl-bundle-name=agent-client",
            "aiops.agent.grant.issuer=aegisops-app",
            "aiops.agent.grant.key-id=task-grant-v1",
            "aiops.agent.grant.private-key-file=" + privateKeyFile,
            "aiops.agent.grant.ttl-seconds=300")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AiAgentClient.class);
              assertThat(context).hasSingleBean(WorkRecordAiClient.class);
              assertThat(context.getBean(AiAgentClient.class))
                  .isInstanceOf(HttpAiAgentClient.class);
              assertThat(context.getBean(WorkRecordAiClient.class))
                  .isInstanceOf(HttpWorkRecordAiClient.class);
              assertThat(context).doesNotHaveBean("agentCredentialProvider");
              assertThat(context).hasSingleBean(DiagnosisGrantProvider.class);
              assertThat(context).doesNotHaveBean(DisabledAgentClients.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    DisabledAgentClients.class,
    HttpAiAgentClient.class,
    HttpWorkRecordAiClient.class,
    SignedDiagnosisGrantProvider.class
  })
  static class AgentClientTestConfiguration {}
}
