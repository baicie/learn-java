package io.aegisops.workrecord.application.command;

public record WorkRecordExportResult(
    String fileName,
    byte[] content,
    int rowCount) {}
