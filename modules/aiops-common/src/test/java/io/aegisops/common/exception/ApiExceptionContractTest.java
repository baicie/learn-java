package io.aegisops.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiExceptionContractTest {

  @Test
  void conflictExceptionMustMapToHttp409() {
    ConflictException ex = new ConflictException("archived template cannot be enabled");
    assertThat(ex.errorCode()).isEqualTo("CONFLICT");
    assertThat(ex.httpStatus()).isEqualTo(409);
  }

  @Test
  void resourceNotFoundExceptionMustMapToHttp404() {
    ResourceNotFoundException ex = new ResourceNotFoundException("work record not found: r1");
    assertThat(ex.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
    assertThat(ex.httpStatus()).isEqualTo(404);
  }

  @Test
  void legacyNotFoundExceptionMustMapToHttp404() {
    NotFoundException ex = new NotFoundException("legacy path");
    assertThat(ex.errorCode()).isEqualTo("NOT_FOUND");
    assertThat(ex.httpStatus()).isEqualTo(404);
  }

  @Test
  void errorCodeEnumMustExposeP0Constants() {
    assertThat(ErrorCode.RESOURCE_NOT_FOUND.httpStatus()).isEqualTo(404);
    assertThat(ErrorCode.NOT_FOUND.httpStatus()).isEqualTo(404);
    assertThat(ErrorCode.CONFLICT.httpStatus()).isEqualTo(409);
    assertThat(ErrorCode.METHOD_NOT_ALLOWED.httpStatus()).isEqualTo(405);
    assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.httpStatus()).isEqualTo(415);
    assertThat(ErrorCode.UNAUTHORIZED.httpStatus()).isEqualTo(401);
    assertThat(ErrorCode.FORBIDDEN.httpStatus()).isEqualTo(403);
    assertThat(ErrorCode.RATE_LIMITED.httpStatus()).isEqualTo(429);
    assertThat(ErrorCode.RATE_LIMIT_BACKEND_UNAVAILABLE.httpStatus()).isEqualTo(503);
    assertThat(ErrorCode.PAGE_WINDOW_EXCEEDED.httpStatus()).isEqualTo(422);
  }
}
