package io.aegisops.platform.calendar;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform")
public class CalendarController {
  private final CalendarService service;

  public CalendarController(CalendarService service) {
    this.service = service;
  }

  @GetMapping("/calendars")
  @PreAuthorize("hasAuthority('platform:calendar:read')")
  public ApiResponse<List<CalendarRecord>> listCalendars() {
    return ApiResponse.ok(service.listCalendars(TenantContext.requireTenantId()));
  }

  @GetMapping("/calendars/{calendarId}/days")
  @PreAuthorize("hasAuthority('platform:calendar:read')")
  public ApiResponse<List<CalendarDayRecord>> listDays(
      @PathVariable String calendarId, @RequestParam LocalDate start, @RequestParam LocalDate end) {
    return ApiResponse.ok(
        service.listDays(TenantContext.requireTenantId(), calendarId, start, end));
  }

  @PutMapping("/calendars/{calendarId}/days/{date}")
  @PreAuthorize("hasAuthority('platform:calendar:write')")
  public ApiResponse<CalendarDayRecord> updateDay(
      @PathVariable String calendarId,
      @PathVariable LocalDate date,
      @RequestBody UpdateCalendarDayRequest request,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.updateDay(
            TenantContext.requireTenantId(),
            calendarId,
            date,
            request,
            user == null ? "system" : user.id()));
  }

  @GetMapping("/calendars/import-template")
  @PreAuthorize("hasAuthority('platform:calendar:import')")
  public ResponseEntity<byte[]> downloadImportTemplate(@RequestParam int year) {
    String filename = "法定节假日-" + year + "-导入模板.xlsx";
    String disposition =
        ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .body(service.createHolidayImportTemplate(year));
  }

  @PostMapping(
      value = "/calendars/{calendarId}/days/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('platform:calendar:import')")
  public ApiResponse<Integer> importXlsx(
      @PathVariable String calendarId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.importXlsx(
            TenantContext.requireTenantId(),
            calendarId,
            file,
            user == null ? "system" : user.id()));
  }

  @GetMapping("/calendar-days/check")
  @PreAuthorize("hasAuthority('platform:calendar:read')")
  public ApiResponse<WorkdayCheckResponse> check(
      @RequestParam String calendarId, @RequestParam LocalDate date) {
    return ApiResponse.ok(service.checkWorkday(TenantContext.requireTenantId(), calendarId, date));
  }

  @GetMapping("/calendar-days/range")
  @PreAuthorize("hasAuthority('platform:calendar:read')")
  public ApiResponse<List<CalendarDayRecord>> range(
      @RequestParam String calendarId, @RequestParam LocalDate start, @RequestParam LocalDate end) {
    return ApiResponse.ok(
        service.listDays(TenantContext.requireTenantId(), calendarId, start, end));
  }

  @GetMapping("/calendar-days/workdays/count")
  @PreAuthorize("hasAuthority('platform:calendar:read')")
  public ApiResponse<WorkdayCountResponse> count(
      @RequestParam String calendarId, @RequestParam LocalDate start, @RequestParam LocalDate end) {
    return ApiResponse.ok(
        service.countWorkdays(TenantContext.requireTenantId(), calendarId, start, end));
  }
}
