package io.aegisops.workrecord.application.command;

import java.time.OffsetDateTime;
import java.util.List;

public record RecordQuery(
    int page,
    int pageSize,
    String templateId,
    String templateVersionId,
    List<String> statuses,
    String keyword,
    OffsetDateTime recordTimeFrom,
    OffsetDateTime recordTimeTo,
    String creatorId,
    String ownerId,
    boolean onlySelf,
    String currentUserId) {}
