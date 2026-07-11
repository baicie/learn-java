package io.aegisops.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

  @Test
  void unexpectedErrorMustNotLeakInternalMessage() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);

    Mockito.when(request.getMethod()).thenReturn("POST");
    Mockito.when(request.getRequestURI()).thenReturn("/api/work-record/records");

    MDC.put("requestId", "req-1");

    try {
      var response =
          handler.handleUnexpected(
              new RuntimeException("relation secret_table does not exist"), request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

      ApiResponse<Void> body = response.getBody();

      assertThat(body).isNotNull();
      assertThat(body.errorCode()).isEqualTo("INTERNAL_ERROR");
      assertThat(body.message()).isEqualTo("internal server error");
      assertThat(body.message()).doesNotContain("secret_table");
      assertThat(body.requestId()).isEqualTo("req-1");
    } finally {
      MDC.clear();
    }
  }
}
