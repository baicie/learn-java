package io.aegisops.platform.iam.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleData(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 128) String name,
    @Size(max = 500) String description,
    boolean system,
    boolean enabled,
    Set<String> permissionCodes) {}