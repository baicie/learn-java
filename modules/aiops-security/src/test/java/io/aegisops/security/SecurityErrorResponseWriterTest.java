package io.aegisops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityErrorResponseWriterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SecurityErrorResponseWriter writer =
      new SecurityErrorResponseWriter(objectMapper);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void mustWriteUniformApiResponseShape() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, 429, "RATE_LIMITED", "request rate limit exceeded", 30L);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getHeader("Retry-After")).isEqualTo("30");

    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("success").asBoolean()).isFalse();
    assertThat(body.path("errorCode").asText()).isEqualTo("RATE_LIMITED");
    assertThat(body.path("message").asText()).isEqualTo("request rate limit exceeded");
    assertThat(body.path("data").isNull()).isTrue();
    assertThat(body.path("timestamp").isMissingNode()).isFalse();
  }

  @Test
  void mustIncludeRequestIdFromMdc() throws Exception {
    MDC.put("requestId", "req_test_abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, 401, "UNAUTHORIZED", "authentication required");

    JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("requestId").asText()).isEqualTo("req_test_abc");
  }

  @Test
  void retryAfterHeaderMustBeAtLeastOne() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, 429, "RATE_LIMITED", "too many requests", 0L);

    assertThat(response.getHeader("Retry-After")).isEqualTo("1");
  }

  @Test
  void mustNotWriteAfterResponseCommitted() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setCommitted(true);

    assertThatThrownBy(
            () -> writer.write(response, 503, "RATE_LIMIT_BACKEND_UNAVAILABLE", "down"))
        .doesNotThrowAnyException();
  }
}
