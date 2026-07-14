package io.aegisops.platform.iam.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.platform.iam.error.IamDomainException;
import io.aegisops.platform.iam.error.IamErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class IamExceptionHandlerTest {

  private final IamExceptionHandler handler = new IamExceptionHandler();

  @Test
  void should_map_iam_domain_exception_to_status() {
    IamDomainException ex =
        new IamDomainException(
            IamErrorCode.USERNAME_CONFLICT, "username taken", Map.of("username", "alice"));
    ResponseEntity<Map<String, Object>> response = handler.handle(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody())
        .containsKeys("ok", "code", "httpStatus", "message", "details")
        .containsEntry("ok", false);
    assertThat(response.getBody().get("code")).isEqualTo(IamErrorCode.USERNAME_CONFLICT.code());
  }

  @Test
  void should_fall_back_to_400_for_illegal_arguments() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleValidation(new IllegalArgumentException("nope"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("message", "nope");
  }

  @Test
  void should_default_message_when_illegal_argument_has_no_message() {
    ResponseEntity<Map<String, Object>> response =
        handler.handleValidation(new IllegalArgumentException());
    assertThat(response.getBody()).containsEntry("message", "invalid argument");
  }
}
