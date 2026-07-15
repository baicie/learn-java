package io.aegisops.workrecord.application.command;

import java.util.List;

public record AsyncExportJobRequest(RecordQuery query, List<String> columns, int maxRows) {}
