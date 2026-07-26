package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.aegisops.platform.audit.PlatformAuditService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class CalendarServiceTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String CALENDAR_ID = "cal-1";
  private static final String XLSX_MEDIA_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  private final CalendarRepository repository = mock(CalendarRepository.class);
  private final PlatformAuditService audit = mock(PlatformAuditService.class);
  private final CalendarService service = new CalendarService(repository, audit);

  @Test
  void shouldListOnlyAutomaticallyGeneratedCalendarsInSupportedRange() {
    List<CalendarRecord> calendars =
        List.of(
            calendar("cal-1999", 1999), calendar(2000), calendar(2050), calendar("cal-2051", 2051));
    when(repository.listCalendars(TENANT_ID)).thenReturn(calendars);

    assertThat(service.listCalendars(TENANT_ID))
        .extracting(CalendarRecord::year)
        .containsExactly(2000, 2050);

    verify(repository).listCalendars(TENANT_ID);
  }

  @Test
  void shouldReturnDerivedWorkdayWhenDayNotConfigured() {
    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2026)));
    when(repository.findDay(TENANT_ID, CALENDAR_ID, LocalDate.of(2026, 7, 9)))
        .thenReturn(Optional.empty());

    WorkdayCheckResponse response =
        service.checkWorkday(TENANT_ID, CALENDAR_ID, LocalDate.of(2026, 7, 9));

    assertThat(response.workday()).isTrue();
    assertThat(response.dayType()).isEqualTo("WORKDAY");
  }

  @Test
  void shouldReturnConfiguredHoliday() {
    LocalDate date = LocalDate.of(2026, 1, 1);
    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2026)));
    when(repository.findDay(TENANT_ID, CALENDAR_ID, date))
        .thenReturn(Optional.of(day(date, "HOLIDAY", false, "New Year's Day", "xlsx")));

    WorkdayCheckResponse response = service.checkWorkday(TENANT_ID, CALENDAR_ID, date);

    assertThat(response.workday()).isFalse();
    assertThat(response.holidayName()).isEqualTo("New Year's Day");
  }

  @Test
  void shouldCountDerivedWorkdaysWhenDaysAreMissing() {
    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2026)));
    when(repository.listDays(
            TENANT_ID, CALENDAR_ID, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12)))
        .thenReturn(List.of());

    WorkdayCountResponse response =
        service.countWorkdays(
            TENANT_ID, CALENDAR_ID, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12));

    assertThat(response.workdays()).isEqualTo(5);
  }

  @Test
  void updateDayShouldAuditBeforeAndAfter() {
    LocalDate date = LocalDate.of(2026, 10, 1);
    CalendarDayRecord before = day(date, "WORKDAY", true, null, "generated");
    CalendarDayRecord after = day(date, "HOLIDAY", false, "National Day", "manual");

    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2026)));
    when(repository.findDay(TENANT_ID, CALENDAR_ID, date)).thenReturn(Optional.of(before));
    when(repository.upsertDay(
            eq(TENANT_ID), eq(CALENDAR_ID), any(CalendarDayMutation.class), anyString()))
        .thenReturn(after);

    service.updateDay(
        TENANT_ID,
        CALENDAR_ID,
        date,
        new UpdateCalendarDayRequest("HOLIDAY", false, null, "National Day", "manual", null),
        "u1");

    verify(audit)
        .recordChange(
            eq(TENANT_ID),
            eq("u1"),
            eq("platform.calendar.day.override"),
            eq("platform_calendar_day"),
            eq(after.id()),
            any(),
            any(),
            any());
  }

  @Test
  void importXlsxShouldAuditGeneratedDayOverwriteAndSummary() {
    LocalDate date = LocalDate.of(2026, 10, 1);
    CalendarDayRecord before = day(date, "WORKDAY", true, null, "generated");
    CalendarDayRecord after = day(date, "HOLIDAY", false, "National Day", "xlsx");

    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2026)));
    when(repository.findDay(TENANT_ID, CALENDAR_ID, date)).thenReturn(Optional.of(before));
    when(repository.upsertDay(
            eq(TENANT_ID), eq(CALENDAR_ID), any(CalendarDayMutation.class), eq("u1")))
        .thenReturn(after);

    int imported =
        service.importXlsx(
            TENANT_ID,
            CALENDAR_ID,
            xlsxFile(new XlsxRow("2026-10-01", "National Day", null)),
            "u1");

    assertThat(imported).isEqualTo(1);

    ArgumentCaptor<CalendarDayMutation> mutation =
        ArgumentCaptor.forClass(CalendarDayMutation.class);
    verify(repository).upsertDay(eq(TENANT_ID), eq(CALENDAR_ID), mutation.capture(), eq("u1"));
    assertThat(mutation.getValue().date()).isEqualTo(date);
    assertThat(mutation.getValue().dayType()).isEqualTo("HOLIDAY");
    assertThat(mutation.getValue().workday()).isFalse();
    assertThat(mutation.getValue().sourceType()).isEqualTo("xlsx");

    ArgumentCaptor<String> actions = ArgumentCaptor.forClass(String.class);
    verify(audit, times(2))
        .recordChange(
            eq(TENANT_ID),
            eq("u1"),
            actions.capture(),
            anyString(),
            anyString(),
            any(),
            any(),
            any());

    assertThat(actions.getAllValues())
        .containsExactlyInAnyOrder(
            "platform.calendar.day.import_overwrite", "platform.calendar.import");
  }

  @Test
  void importXlsxShouldRejectNonXlsxFileBeforeReadingCalendar() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "holidays.csv",
            "text/csv",
            "2026-01-01,HOLIDAY".getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> service.importXlsx(TENANT_ID, CALENDAR_ID, file, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be an xlsx file");

    verifyNoInteractions(repository, audit);
  }

  @Test
  void importXlsxShouldRejectFileLargerThanFiveMegabytes() {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "holidays.xlsx", XLSX_MEDIA_TYPE, new byte[5 * 1024 * 1024 + 1]);

    assertThatThrownBy(() -> service.importXlsx(TENANT_ID, CALENDAR_ID, file, "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not exceed 5 MB");

    verifyNoInteractions(repository, audit);
  }

  @Test
  void importXlsxShouldRejectHolidayOutsideSelectedCalendarYear() {
    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2026)));

    assertThatThrownBy(
            () ->
                service.importXlsx(
                    TENANT_ID,
                    CALENDAR_ID,
                    xlsxFile(new XlsxRow("2027-01-01", "New Year's Day", null)),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("selected calendar year");

    verify(repository, never())
        .upsertDay(anyString(), anyString(), any(CalendarDayMutation.class), anyString());
    verifyNoInteractions(audit);
  }

  @Test
  void shouldCreateHolidayTemplateForRequestedYear() {
    var rows = new CalendarXlsxImporter().parse(service.createHolidayImportTemplate(2050));

    assertThat(rows)
        .singleElement()
        .satisfies(row -> assertThat(row.date()).isEqualTo(LocalDate.of(2050, 1, 1)));
    verifyNoInteractions(repository, audit);
  }

  @Test
  void shouldRejectHolidayTemplateYearOutsideSupportedRange() {
    assertThatThrownBy(() -> service.createHolidayImportTemplate(1999))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");
    assertThatThrownBy(() -> service.createHolidayImportTemplate(2051))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");

    verifyNoInteractions(repository, audit);
  }

  @Test
  void shouldRejectLegacyCalendarAcrossDayOperations() {
    LocalDate date = LocalDate.of(2051, 1, 1);
    when(repository.findCalendar(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar(2051)));

    assertThatThrownBy(() -> service.listDays(TENANT_ID, CALENDAR_ID, date, date))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");
    assertThatThrownBy(
            () ->
                service.updateDay(
                    TENANT_ID,
                    CALENDAR_ID,
                    date,
                    new UpdateCalendarDayRequest(
                        "HOLIDAY", false, null, "Legacy holiday", "manual", null),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");
    assertThatThrownBy(() -> service.checkWorkday(TENANT_ID, CALENDAR_ID, date))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");
    assertThatThrownBy(() -> service.countWorkdays(TENANT_ID, CALENDAR_ID, date, date))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");
    assertThatThrownBy(
            () ->
                service.importXlsx(
                    TENANT_ID,
                    CALENDAR_ID,
                    xlsxFile(new XlsxRow("2051-01-01", "Legacy holiday", null)),
                    "u1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 2000 and 2050");

    verify(repository, never()).listDays(anyString(), anyString(), any(), any());
    verify(repository, never()).findDay(anyString(), anyString(), any());
    verify(repository, never())
        .upsertDay(anyString(), anyString(), any(CalendarDayMutation.class), anyString());
    verifyNoInteractions(audit);
  }

  private CalendarRecord calendar(int year) {
    return calendar(CALENDAR_ID, year);
  }

  private CalendarRecord calendar(String id, int year) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    return new CalendarRecord(
        id,
        TENANT_ID,
        "CN_" + year,
        "China Mainland " + year + " Work Calendar",
        "CN",
        "Asia/Shanghai",
        year,
        true,
        "generated",
        null,
        "system",
        now,
        now);
  }

  private CalendarDayRecord day(
      LocalDate date, String dayType, boolean workday, String holidayName, String sourceType) {
    OffsetDateTime now = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    return new CalendarDayRecord(
        "day-" + date,
        TENANT_ID,
        CALENDAR_ID,
        date,
        date.getDayOfWeek().getValue(),
        dayType,
        workday,
        null,
        holidayName,
        sourceType,
        null,
        "system",
        now,
        now);
  }

  private MockMultipartFile xlsxFile(XlsxRow... rows) {
    return new MockMultipartFile("file", "holidays.xlsx", XLSX_MEDIA_TYPE, xlsx(rows));
  }

  private byte[] xlsx(XlsxRow... rows) {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Holiday exceptions");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("date");
      header.createCell(1).setCellValue("holidayName");
      header.createCell(2).setCellValue("remark");

      for (int index = 0; index < rows.length; index++) {
        XlsxRow source = rows[index];
        Row row = sheet.createRow(index + 1);
        row.createCell(0).setCellValue(source.date());
        if (source.holidayName() != null) {
          row.createCell(1).setCellValue(source.holidayName());
        }
        if (source.remark() != null) {
          row.createCell(2).setCellValue(source.remark());
        }
      }

      workbook.write(output);
      return output.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("unable to create xlsx test data", ex);
    }
  }

  private record XlsxRow(String date, String holidayName, String remark) {}
}
