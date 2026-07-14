package io.aegisops.platform.iam.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreatePlatformUserData(
    @NotBlank @Size(min = 3, max = 64) String username,
    @NotBlank @Size(max = 128) String displayName,
    @Email @Size(max = 128) String email,
    @NotBlank @Size(min = 8, max = 128) String initialPassword,
    @NotBlank String status,
    Set<String> roleCodes) {

  public void validate() {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("username is required");
    }
    if (initialPassword == null || initialPassword.length() < 8) {
      throw new IllegalArgumentException("initialPassword must be at least 8 chars");
    }
  }
}
