package io.aegisops.runbook.dto;

import java.math.BigDecimal;
import java.util.List;

public record RunbookMatchResult(
    RunbookRecord runbook, int score, BigDecimal confidence, List<String> reasons) {}
