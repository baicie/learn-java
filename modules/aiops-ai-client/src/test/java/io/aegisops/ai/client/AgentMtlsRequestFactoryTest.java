package io.aegisops.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.JdkClientHttpRequestFactory;

class AgentMtlsRequestFactoryTest {
  @Test
  void buildsJdkClientFromConfiguredPemSslBundle() throws Exception {
    SslBundles bundles = mock(SslBundles.class);
    SslBundle bundle = mock(SslBundle.class);
    when(bundles.getBundle("agent-client")).thenReturn(bundle);
    when(bundle.createSslContext()).thenReturn(SSLContext.getDefault());
    AgentClientProperties properties =
        new AgentClientProperties("https://aiops-agent:9008", 1234, 5678, "agent-client");

    assertThat(AgentMtlsRequestFactory.create(properties, bundles))
        .isInstanceOf(JdkClientHttpRequestFactory.class);
  }

  @Test
  void rejectsPlainHttpWhenAgentIsEnabled() {
    AgentClientProperties properties =
        new AgentClientProperties("http://aiops-agent:9008", 1234, 5678, "agent-client");

    assertThatThrownBy(() -> AgentMtlsRequestFactory.create(properties, mock(SslBundles.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("https");
  }
}
