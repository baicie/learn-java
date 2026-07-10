package io.aegisops.audit;

import com.fasterxml.jackson.databind.JsonNode;

/** One field-level change detected between a before snapshot and an after snapshot. */
public record AuditChange(String path, JsonNode beforeValue, JsonNode afterValue) {}
