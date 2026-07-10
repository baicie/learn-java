package io.aegisops.platform.calendar;

import io.aegisops.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WorkCalendarExceptionHandler {

  @ExceptionHandler(WorkCalendarConfigurationException.class)
  public ResponseEntity<ApiResponse<Void>> handleWorkCalendarError(
      WorkCalendarConfigurationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.fail(ex.errorCode(), ex.getMessage()));
  }
}
