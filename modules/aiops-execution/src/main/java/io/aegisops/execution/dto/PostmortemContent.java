package io.aegisops.execution.dto;

import java.util.List;

/** DTO for building postmortem markdown content. */
public record PostmortemContent(
    PostmortemSourceBundle source,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    List<String> actionItems) {}
