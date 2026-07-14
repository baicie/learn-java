package io.aegisops.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * {@link ApiResponse} 必须自动从 MDC 注入当前请求 ID， 这样 Controller 调用 {@code ApiResponse.ok(data)} 即可在 body
 * 中包含 requestId。
 */
class ApiResponseTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void okMustPropagateRequestIdFromMdc() {
    MDC.put("requestId", "req-abc-123");

    ApiResponse<String> response = ApiResponse.ok("payload");

    assertThat(response.success()).isTrue();
    assertThat(response.data()).isEqualTo("payload");
    assertThat(response.requestId()).isEqualTo("req-abc-123");
    assertThat(response.timestamp()).isNotNull();
  }

  @Test
  void okMustUseNullWhenMdcRequestIdMissing() {
    ApiResponse<String> response = ApiResponse.ok("payload");

    assertThat(response.requestId()).isNull();
  }

  @Test
  void explicitRequestIdOverridesMdc() {
    MDC.put("requestId", "from-mdc");

    ApiResponse<String> response = ApiResponse.ok("payload", "explicit");

    assertThat(response.requestId()).isEqualTo("explicit");
  }

  @Test
  void failMustPropagateRequestIdFromMdc() {
    MDC.put("requestId", "req-xyz");

    ApiResponse<Void> response = ApiResponse.fail("BAD_REQUEST", "boom");

    assertThat(response.success()).isFalse();
    assertThat(response.errorCode()).isEqualTo("BAD_REQUEST");
    assertThat(response.message()).isEqualTo("boom");
    assertThat(response.requestId()).isEqualTo("req-xyz");
  }

  @Test
  void failMustUseNullWhenMdcRequestIdMissing() {
    ApiResponse<Void> response = ApiResponse.fail("BAD_REQUEST", "boom");

    assertThat(response.requestId()).isNull();
  }

  @Test
  void legacyFiveArgConstructorMustPickUpMdc() {
    MDC.put("requestId", "legacy");

    ApiResponse<String> response =
        new ApiResponse<>(true, "x", null, null, java.time.OffsetDateTime.now());

    assertThat(response.requestId()).isEqualTo("legacy");
  }

  @Test
  void blankMdcValueTreatedAsMissing() {
    MDC.put("requestId", "   ");

    ApiResponse<String> response = ApiResponse.ok("payload");

    assertThat(response.requestId()).isNull();
  }
}
