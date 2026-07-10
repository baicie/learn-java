package io.aegisops.workrecord.application.command;

public record CopyTemplateCommand(
    String sourceTemplateId, String targetCode, String targetName, String description) {}
