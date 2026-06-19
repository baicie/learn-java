package io.aegisops.execution;

import java.util.List;

public record PostmortemDraft(
    String title,
    String summary,
    String impact,
    String rootCause,
    String detection,
    String resolution,
    String prevention,
    List<String> actionItems) {}
