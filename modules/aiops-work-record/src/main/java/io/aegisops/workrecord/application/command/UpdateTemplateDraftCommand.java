package io.aegisops.workrecord.application.command;

public record UpdateTemplateDraftCommand(
    String name, String description, String draftSchemaJson, String draftDesignerJson) {}
