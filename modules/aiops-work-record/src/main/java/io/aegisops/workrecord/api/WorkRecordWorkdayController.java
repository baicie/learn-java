package io.aegisops.workrecord.api;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.workrecord.application.command.RecordWorkdaySummary;
import io.aegisops.workrecord.application.service.WorkRecordWorkdaySummaryService;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work-record/records/workdays")
public class WorkRecordWorkdayController {
  private final WorkRecordWorkdaySummaryService service;

  public WorkRecordWorkdayController(WorkRecordWorkdaySummaryService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  @PreAuthorize(
      "hasAuthority('work-record:read:all') " + "or hasAuthority('work-record:read:self')")
  public ApiResponse<RecordWorkdaySummary> summary(@RequestParam(required = false) String month) {
    return ApiResponse.ok(service.month(TenantContext.requireTenantId(), parseMonth(month)));
  }

  YearMonth parseMonth(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    try {
      return YearMonth.parse(value);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException("month must use yyyy-MM format", ex);
    }
  }
}
