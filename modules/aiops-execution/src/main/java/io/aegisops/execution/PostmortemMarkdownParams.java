package io.aegisops.execution;

import java.util.List;

/** DTO for building postmortem markdown. */
public record PostmortemMarkdownParams(
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    List<String> actionItems) {}
