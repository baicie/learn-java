package io.aegisops.platform.iam.domain;

public record ChangeUserStatusCommand(String status, String reason, int rowVersion) {}