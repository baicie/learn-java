package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** Unit tests for {@link ConsolePaths}. */
class ConsolePathsTest {
  @Test
  void shouldTreatRootAsConsolePage() {
    MockHttpServletRequest request = htmlGet("/");

    assertThat(ConsolePaths.isConsolePageRequest(request)).isTrue();
    assertThat(ConsolePaths.isConsoleRequest(request)).isTrue();
  }

  @Test
  void shouldTreatBrowserRouteAsConsolePage() {
    MockHttpServletRequest request = htmlGet("/incidents/incident-1");

    assertThat(ConsolePaths.isConsolePageRequest(request)).isTrue();
    assertThat(ConsolePaths.isConsoleRequest(request)).isTrue();
  }

  @Test
  void shouldTreatNestedBrowserRouteAsConsolePage() {
    MockHttpServletRequest request = htmlGet("/datasources/zabbix/config");

    assertThat(ConsolePaths.isConsolePageRequest(request)).isTrue();
  }

  @Test
  void shouldNotTreatJsonNavigationAsConsolePage() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/incidents");
    request.addHeader("Accept", "application/json");

    assertThat(ConsolePaths.isConsolePageRequest(request)).isFalse();
  }

  @Test
  void shouldTreatViteAssetAsConsoleAsset() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/index-a1b2c3.js");
    request.addHeader("Accept", "*/*");

    assertThat(ConsolePaths.isConsoleAssetRequest(request)).isTrue();
    assertThat(ConsolePaths.isConsolePageRequest(request)).isFalse();
    assertThat(ConsolePaths.isConsoleRequest(request)).isTrue();
  }

  @Test
  void shouldTreatFaviconAsConsoleAsset() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");
    request.addHeader("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");

    assertThat(ConsolePaths.isConsoleAssetRequest(request)).isTrue();
  }

  @Test
  void shouldNotTreatApiAsConsoleRequest() {
    MockHttpServletRequest request = htmlGet("/api/incidents");

    assertThat(ConsolePaths.isConsoleRequest(request)).isFalse();
    assertThat(ConsolePaths.isBackendPath("/api/incidents")).isTrue();
  }

  @Test
  void shouldNotTreatActuatorAsConsoleRequest() {
    MockHttpServletRequest request = htmlGet("/actuator/health");

    assertThat(ConsolePaths.isConsoleRequest(request)).isFalse();
    assertThat(ConsolePaths.isBackendPath("/actuator/health")).isTrue();
  }

  @Test
  void shouldNotTreatSwaggerAsConsoleRequest() {
    MockHttpServletRequest request = htmlGet("/swagger-ui/index.html");

    assertThat(ConsolePaths.isConsoleRequest(request)).isFalse();
    assertThat(ConsolePaths.isBackendPath("/swagger-ui/index.html")).isTrue();
  }

  @Test
  void shouldNotTreatInternalAgentApiAsConsoleRequest() {
    MockHttpServletRequest request = htmlGet("/internal/agent/evidence");

    assertThat(ConsolePaths.isConsoleRequest(request)).isFalse();
    assertThat(ConsolePaths.isBackendPath("/internal/agent/evidence")).isTrue();
  }

  @Test
  void shouldNotTreatPostAsConsoleRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/incidents");
    request.addHeader("Accept", "text/html");

    assertThat(ConsolePaths.isConsoleRequest(request)).isFalse();
  }

  @Test
  void shouldSupportContextPath() {
    MockHttpServletRequest request = htmlGet("/aegisops/incidents/incident-1");
    request.setContextPath("/aegisops");

    assertThat(ConsolePaths.isConsolePageRequest(request)).isTrue();
  }

  private static MockHttpServletRequest htmlGet(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
    return request;
  }
}
