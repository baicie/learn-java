package io.aegisops.ai.client.workrecord;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.ai.client.AgentClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HttpWorkRecordAiClientTest {
  @Test
  void appliesConfiguredConnectAndReadTimeouts() {
    var properties = new AgentClientProperties("http://agent:9008", "token", 4321, 90000);

    var factory = HttpWorkRecordAiClient.createRequestFactory(properties);

    assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(4321);
    assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(90000);
  }
}
