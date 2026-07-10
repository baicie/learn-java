package io.aegisops.workrecord.application.command;

public record CreateTemplateCommand(
    String code,
    String name,
    String description,
    String draftSchemaJson,
    String draftDesignerJson) {}
