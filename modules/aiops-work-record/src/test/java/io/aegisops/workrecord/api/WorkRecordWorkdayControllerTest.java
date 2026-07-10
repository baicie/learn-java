package io.aegisops.workrecord.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.aegisops.workrecord.application.service.WorkRecordWorkdaySummaryService;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class WorkRecordWorkdayControllerTest {
  private final WorkRecordWorkdayController controller =
      new WorkRecordWorkdayController(mock(WorkRecordWorkdaySummaryService.class));

  @Test
  void shouldParseYearMonth() {
    assertThat(controller.parseMonth("2026-07")).isEqualTo(YearMonth.of(2026, 7));
  }

  @Test
  void shouldTreatBlankMonthAsCurrentMonth() {
    assertThat(controller.parseMonth("")).isNull();
  }

  @Test
  void shouldRejectInvalidMonth() {
    assertThatThrownBy(() -> controller.parseMonth("2026-7"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("yyyy-MM");
  }
}
