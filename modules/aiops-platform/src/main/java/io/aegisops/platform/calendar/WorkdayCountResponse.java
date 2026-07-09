package io.aegisops.platform.calendar;

import java.time.LocalDate;

public record WorkdayCountResponse(LocalDate start, LocalDate end, int workdays) {}
