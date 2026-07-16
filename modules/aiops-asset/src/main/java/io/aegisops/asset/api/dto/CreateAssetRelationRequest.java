package io.aegisops.asset.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAssetRelationRequest(
    @NotBlank String targetAssetId,
    @NotBlank @Size(max = 64) String relationType,
    @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence) {}
