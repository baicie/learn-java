package io.aegisops.server.console;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Unit tests for {@link ConsoleSpaForwardConfiguration#shouldForward}. */
class ConsoleSpaForwardConfigurationTest {
  @Test
  void shouldForwardRootWhenConsoleExists() {
    MockHttpServletRequest request = htmlGet("/");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isTrue();
  }

  @Test
  void shouldForwardBrowserRouteWhenConsoleExists() {
    MockHttpServletRequest request = htmlGet("/incidents/incident-1");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isTrue();
  }

  @Test
  void shouldNotForwardWhenConsoleDoesNotExist() {
    MockHttpServletRequest request = htmlGet("/incidents/incident-1");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, false)).isFalse();
  }

  @Test
  void shouldNotForwardApiRequest() {
    MockHttpServletRequest request = htmlGet("/api/incidents");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isFalse();
  }

  @Test
  void shouldNotForwardActuatorRequest() {
    MockHttpServletRequest request = htmlGet("/actuator/health");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isFalse();
  }

  @Test
  void shouldNotForwardSwaggerRequest() {
    MockHttpServletRequest request = htmlGet("/swagger-ui/index.html");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isFalse();
  }

  @Test
  void shouldNotForwardStaticAsset() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/index-a1b2c3.js");
    request.addHeader("Accept", "*/*");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isFalse();
  }

  @Test
  void shouldNotForwardPostRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/reports");
    request.addHeader("Accept", "text/html");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isFalse();
  }

  @Test
  void shouldNotForwardJsonRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reports");
    request.addHeader("Accept", "application/json");

    assertThat(ConsoleSpaForwardConfiguration.shouldForward(request, true)).isFalse();
  }

  private static MockHttpServletRequest htmlGet(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    return request;
  }
}
