package io.aegisops.web;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import io.aegisops.web.request.RequestBodyTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
    return response(ex.httpStatus(), ex.errorCode(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));

    return response(
        HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_ERROR.name(), message);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
    return response(
        HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_ERROR.name(), ex.getMessage());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
    if (hasCause(ex, RequestBodyTooLargeException.class)) {
      return response(
          HttpStatus.PAYLOAD_TOO_LARGE.value(),
          ErrorCode.PAYLOAD_TOO_LARGE.name(),
          "request body is too large");
    }

    return response(
        HttpStatus.BAD_REQUEST.value(),
        ErrorCode.MALFORMED_REQUEST.name(),
        "request body is malformed");
  }

  @ExceptionHandler(RequestBodyTooLargeException.class)
  public ResponseEntity<ApiResponse<Void>> handleTooLarge(RequestBodyTooLargeException ex) {
    return response(
        HttpStatus.PAYLOAD_TOO_LARGE.value(),
        ErrorCode.PAYLOAD_TOO_LARGE.name(),
        ex.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException ex) {
    return response(
        HttpStatus.BAD_REQUEST.value(), ErrorCode.BAD_REQUEST.name(), ex.getMessage());
  }

  @ExceptionHandler({SecurityException.class, AccessDeniedException.class})
  public ResponseEntity<ApiResponse<Void>> handleForbidden(RuntimeException ex) {
    return response(HttpStatus.FORBIDDEN.value(), ErrorCode.FORBIDDEN.name(), "access denied");
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
    return response(
        HttpStatus.UNAUTHORIZED.value(), ErrorCode.UNAUTHORIZED.name(), "authentication required");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException ex) {
    log.warn("database constraint rejected request, requestId={}", requestId(), ex);

    return response(
        HttpStatus.CONFLICT.value(),
        ErrorCode.CONFLICT.name(),
        "request conflicts with existing data");
  }

  @ExceptionHandler(QueryTimeoutException.class)
  public ResponseEntity<ApiResponse<Void>> handleQueryTimeout(QueryTimeoutException ex) {
    log.error("database query timed out, requestId={}", requestId(), ex);

    return response(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        ErrorCode.DATABASE_TIMEOUT.name(),
        "database query timed out");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
    return response(
        ErrorCode.METHOD_NOT_ALLOWED.httpStatus(),
        ErrorCode.METHOD_NOT_ALLOWED.name(),
        "request method is not supported");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
    return response(
        ErrorCode.UNSUPPORTED_MEDIA_TYPE.httpStatus(),
        ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(),
        "content type is not supported");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error(
        "unexpected API error: method={}, path={}, requestId={}",
        request.getMethod(),
        request.getRequestURI(),
        requestId(),
        ex);

    return response(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        ErrorCode.INTERNAL_ERROR.name(),
        "internal server error");
  }

  private ResponseEntity<ApiResponse<Void>> response(int status, String code, String message) {
    return ResponseEntity.status(status)
        .body(ApiResponse.fail(code, message, requestId()));
  }

  private String requestId() {
    String value = MDC.get("requestId");
    return value == null || value.isBlank() ? null : value;
  }

  private boolean hasCause(Throwable source, Class<? extends Throwable> type) {
    Throwable current = source;
    while (current != null) {
      if (type.isInstance(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}