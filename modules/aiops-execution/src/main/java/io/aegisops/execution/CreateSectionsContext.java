package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemSourceBundle;

/** Context for PostmortemService.createReportSections. */
public record CreateSectionsContext(
    String tenantId,
    String postmortemId,
    PostmortemDraft draft,
    PostmortemSourceBundle source,
    String actor,
    boolean includeActionItems) {}
