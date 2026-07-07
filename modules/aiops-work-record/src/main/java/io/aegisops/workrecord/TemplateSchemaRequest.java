package io.aegisops.workrecord;

import java.util.List;

public record TemplateSchemaRequest(String schemaJson, List<CreateFieldRequest> fields) {}
