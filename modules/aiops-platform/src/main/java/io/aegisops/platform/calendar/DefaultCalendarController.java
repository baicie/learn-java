package io.aegisops.platform.calendar;

import io.aegisops.common.api.ApiResponse;
import io.aegisops.common.tenant.TenantContext;
import io.aegisops.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/calendars")
public class DefaultCalendarController {
  private final DefaultCalendarService service;

  public DefaultCalendarController(DefaultCalendarService service) {
    this.service = service;
  }

  @GetMapping("/default")
  @PreAuthorize("hasAuthority('platform:calendar:read')")
  public ApiResponse<CalendarRecord> getDefault(@RequestParam int year) {
    return ApiResponse.ok(service.getDefaultCalendar(TenantContext.requireTenantId(), year));
  }

  @PutMapping("/{calendarId}/default")
  @PreAuthorize("hasAuthority('platform:calendar:write')")
  public ApiResponse<CalendarRecord> setDefault(
      @PathVariable String calendarId, @AuthenticationPrincipal UserPrincipal user) {
    return ApiResponse.ok(
        service.setDefaultCalendar(
            TenantContext.requireTenantId(), calendarId, user == null ? "system" : user.id()));
  }
}
