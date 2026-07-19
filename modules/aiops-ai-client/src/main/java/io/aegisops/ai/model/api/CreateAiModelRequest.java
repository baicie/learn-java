package io.aegisops.ai.model.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAiModelRequest(
    @NotBlank @Pattern(regexp = "deepseek") String provider,
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 160) String modelName,
    @NotBlank @Size(max = 512) String baseUrl,
    @NotBlank @Size(max = 512) String apiKey,
    boolean enabled) {}
