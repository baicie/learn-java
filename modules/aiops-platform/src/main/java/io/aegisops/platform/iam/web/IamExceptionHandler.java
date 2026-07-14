package io.aegisops.platform.iam.web;

import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.error.IamErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates IAM domain exceptions into a stable JSON envelope so that the Portal front-end can
 * pick up a {@code code} and react with i18n messages.
 */
@RestControllerAdvice(basePackageClasses = PlatformUserController.class)
public class IamExceptionHandler {

  @ExceptionHandler(IamDomainException.class)
  public ResponseEntity<Map<String, Object>> handle(IamDomainException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", false);
    body.put("code", ex.code().code());
    body.put("httpStatus", ex.code().httpStatus());
    body.put("message", ex.getMessage());
    body.put("details", ex.details());
    return ResponseEntity.status(ex.code().httpStatus()).body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            Map.of(
                "ok",
                false,
                "code",
                IamErrorCode.VALIDATION_FAILED.code(),
                "httpStatus",
                IamErrorCode.VALIDATION_FAILED.httpStatus(),
                "message",
                ex.getMessage() == null ? "invalid argument" : ex.getMessage()));
  }
}
