package io.aegisops.integration.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ChangePayloadMapperTest {
  private final ObjectMapper json = new ObjectMapper();
  private final ChangePayloadMapper mapper = new ChangePayloadMapper(json);

  @Test
  void normalizesGithubActionsDeploymentAndHelmPayloads() throws Exception {
    var github =
        mapper.map(
            "github",
            json.readTree(
                """
        {"id":"run-1","created_at":"2026-07-17T10:00:00Z","repository":{"name":"checkout","html_url":"https://github.example/checkout"},"workflow_run":{"name":"deploy","conclusion":"success","head_sha":"abc"},"sender":{"login":"alice"}}
        """));
    var deployment =
        mapper.map(
            "webhook",
            json.readTree(
                """
        {"kind":"deployment","id":"deploy-1","service":"checkout","image":"checkout:1.2","occurredAt":"2026-07-17T10:00:00Z"}
        """));
    var helm =
        mapper.map(
            "webhook",
            json.readTree(
                """
        {"kind":"helm","id":"helm-1","release":"checkout","chart":"checkout-1.2","namespace":"prod","occurredAt":"2026-07-17T10:00:00Z"}
        """));

    assertThat(github.changeType()).isEqualTo("github_actions");
    assertThat(deployment.changeType()).isEqualTo("deployment");
    assertThat(helm.changeType()).isEqualTo("helm");
  }
}
