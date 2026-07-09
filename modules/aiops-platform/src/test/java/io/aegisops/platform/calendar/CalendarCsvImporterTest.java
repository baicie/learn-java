package io.aegisops.platform.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CalendarCsvImporterTest {

  private final CalendarCsvImporter importer = new CalendarCsvImporter();

  @Test
  void shouldParseCalendarCsv() {
    var rows =
        importer.parse(
            """
            date,dayType,isWorkday,holidayName,remark
            2026-01-01,HOLIDAY,false,元旦,
            2026-02-14,ADJUSTED_WORKDAY,true,,春节调休上班
            """);

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(rows.get(0).dayType()).isEqualTo("HOLIDAY");
    assertThat(rows.get(0).workday()).isFalse();
    assertThat(rows.get(0).holidayName()).isEqualTo("元旦");
    assertThat(rows.get(1).workday()).isTrue();
    assertThat(rows.get(1).remark()).isEqualTo("春节调休上班");
  }

  @Test
  void shouldRejectBlankCsv() {
    assertThatThrownBy(() -> importer.parse(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("csv is required");
  }

  @Test
  void shouldRejectInvalidBoolean() {
    assertThatThrownBy(
            () ->
                importer.parse(
                    """
                    date,dayType,isWorkday,holidayName,remark
                    2026-01-01,HOLIDAY,NOPE,元旦,
                    """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid isWorkday");
  }

  @Test
  void shouldRejectInvalidDayType() {
    assertThatThrownBy(
            () ->
                importer.parse(
                    """
                    date,dayType,isWorkday,holidayName,remark
                    2026-01-01,BAD_TYPE,false,元旦,
                    """))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("invalid dayType");
  }
}
