package io.aegisops.inspection;

public record CreateInspectionTaskRequest(
    String name, String targetType, String targetQueryJson, String templateKey) {}
