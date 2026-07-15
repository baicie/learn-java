package io.aegisops.platform.iam.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPlatformUserPasswordCommand(
    @NotBlank @Size(min = 8, max = 128) String newPassword) {}
