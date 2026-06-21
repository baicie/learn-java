package io.aegisops.demo.order;

public record OrderCreateResponse(String orderId, String status, long latencyMs, String message) {}
