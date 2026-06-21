package io.aegisops.demo.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderCreateRequest(
    @NotBlank(message = "skuId is required") String skuId,
    @Min(value = 1, message = "quantity must be greater than 0") int quantity) {}
