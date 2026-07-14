package io.aegisops.workrecord.application.command;

import java.time.OffsetDateTime;

public record StatisticsQuery(
    String templateId,
    String templateVersionId,
    OffsetDateTime from,
    OffsetDateTime to,
    String groupBy,
    String statisticalFieldCode) {}
