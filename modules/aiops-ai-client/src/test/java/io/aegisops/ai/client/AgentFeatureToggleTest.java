package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.workrecord.HttpWorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordAiClient;
import io.aegisops.ai.client.workrecord.WorkRecordGenerationRequest;
import io.aegisops.common.exception.AppException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
                                  null)))
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
          assertThat(context).doesNotHaveBean(AgentCredentialProvider.class);
          assertThat(context).doesNotHaveBean(DiagnosisGrantProvider.class);
          assertThat(context).doesNotHaveBean(HttpAiAgentClient.class);
          assertThat(context).doesNotHaveBean(HttpWorkRecordAiClient.class);
        });
  }

  @Test
  void createsOauthAndHttpClientsOnlyWhenAgentIsEnabled() {
    contextRunner
        .withPropertyValues(
            "aiops.agent.enabled=true",
            "aiops.agent.base-url=http://agent:9008",
            "aiops.agent.auth.token-uri=http://idp/token",
            "aiops.agent.auth.client-id=aiops-server",
            "aiops.agent.auth.client-secret=test-client-secret",
            "aiops.agent.grant.issuer=aiops-server",
            "aiops.agent.grant.audience=aegisops-internal-api",
            "aiops.agent.grant.secret=test-diagnosis-grant-secret-change-me",
            "aiops.agent.grant.ttl-seconds=300")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AiAgentClient.class);
              assertThat(context).hasSingleBean(WorkRecordAiClient.class);
              assertThat(context.getBean(AiAgentClient.class))
                  .isInstanceOf(HttpAiAgentClient.class);
              assertThat(context.getBean(WorkRecordAiClient.class))
                  .isInstanceOf(HttpWorkRecordAiClient.class);
              assertThat(context).hasSingleBean(AgentCredentialProvider.class);
              assertThat(context).hasSingleBean(DiagnosisGrantProvider.class);
              assertThat(context).doesNotHaveBean(DisabledAgentClients.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    AgentServiceAuthConfiguration.class,
    DisabledAgentClients.class,
    HttpAiAgentClient.class,
    HttpWorkRecordAiClient.class,
    SignedDiagnosisGrantProvider.class
  })
  static class AgentClientTestConfiguration {}
}
