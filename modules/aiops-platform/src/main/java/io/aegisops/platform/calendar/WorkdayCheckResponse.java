package io.aegisops.platform.calendar;

import java.time.LocalDate;

public record WorkdayCheckResponse(
    LocalDate date, boolean workday, String dayType, String holidayName) {}
