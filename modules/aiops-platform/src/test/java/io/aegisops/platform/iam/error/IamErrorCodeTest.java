package io.aegisops.platform.iam.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IamErrorCodeTest {

  @Test
  void should_map_username_conflict_to_409() {
    assertThat(IamErrorCode.USERNAME_CONFLICT.httpStatus()).isEqualTo(409);
    assertThat(IamErrorCode.USERNAME_CONFLICT.code()).startsWith("platform.");
  }

  @Test
  void should_map_permission_directory_incomplete_to_422() {
    assertThat(IamErrorCode.PERMISSION_DIRECTORY_INCOMPLETE.httpStatus()).isEqualTo(422);
  }

  @Test
  void should_map_protected_role_to_403() {
    assertThat(IamErrorCode.PROTECTED_ROLE_MODIFIED.httpStatus()).isEqualTo(403);
  }
}
