package io.aegisops.plugin.dto;

public record AgentToolAuthorizeResponse(boolean allowed, String toolKey, String reason) {}
