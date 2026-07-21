package io.aegisops.workrecord.infrastructure.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Repository 必须使用 long offset 避免 int 溢出；钳制 page/size 为至少 1。 */
class JdbcWorkRecordRepositoryPageOffsetTest {

  @Test
  void pageSize_200_page_500_mustUseLongOffset_99800() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Long.class)))
        .thenReturn(100000L);
    when(jdbc.query(any(String.class), any(Map.class), any(RowMapper.class)))
        .thenReturn(Collections.emptyList());

    JdbcWorkRecordRepository repository = new JdbcWorkRecordRepository(jdbc);
    repository.page("t-1", baseQuery(500, 200));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(1)).query(any(String.class), captor.capture(), any(RowMapper.class));

    Map<String, Object> params = captor.getValue();
    Object offsetValue = params.get("offset");
    Object limitValue = params.get("limit");

    assertThat(limitValue).isEqualTo(200);
    assertThat(offsetValue).isEqualTo(99800L);
    assertThat(offsetValue).isInstanceOf(Long.class);
  }

  @Test
  void pageSize_negativeOrZeroIsClampedToOne() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Long.class))).thenReturn(0L);
    when(jdbc.query(any(String.class), any(Map.class), any(RowMapper.class)))
        .thenReturn(Collections.emptyList());

    JdbcWorkRecordRepository repository = new JdbcWorkRecordRepository(jdbc);
    repository.page("t-1", baseQuery(3, 0));
    repository.page("t-1", baseQuery(3, -10));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(2)).query(any(String.class), captor.capture(), any(RowMapper.class));

    List<Map<String, Object>> captured = captor.getAllValues();
    assertThat(captured.get(0).get("limit")).isEqualTo(1);
    assertThat(captured.get(0).get("offset")).isEqualTo(2L);
    assertThat(captured.get(1).get("limit")).isEqualTo(1);
    assertThat(captured.get(1).get("offset")).isEqualTo(2L);
  }

  @Test
  void page_negativeIsClampedToOne() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Long.class))).thenReturn(0L);
    when(jdbc.query(any(String.class), any(Map.class), any(RowMapper.class)))
        .thenReturn(Collections.emptyList());

    JdbcWorkRecordRepository repository = new JdbcWorkRecordRepository(jdbc);
    repository.page("t-1", baseQuery(-3, 50));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(1)).query(any(String.class), captor.capture(), any(RowMapper.class));

    assertThat(captor.getValue().get("offset")).isEqualTo(0L);
    assertThat(captor.getValue().get("limit")).isEqualTo(50);
  }

  @Test
  void pageSizeAtIntegerMaxStillProducesLongOffset() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForObject(any(String.class), any(Map.class), eq(Long.class))).thenReturn(0L);
    when(jdbc.query(any(String.class), any(Map.class), any(RowMapper.class)))
        .thenReturn(Collections.emptyList());

    JdbcWorkRecordRepository repository = new JdbcWorkRecordRepository(jdbc);
    repository.page("t-1", baseQuery(Integer.MAX_VALUE, Integer.MAX_VALUE));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
    verify(jdbc, times(1)).query(any(String.class), captor.capture(), any(RowMapper.class));

    Object offsetValue = captor.getValue().get("offset");

    // 即使 pageSize 和 page 都取 Integer.MAX_VALUE，
    // (page-1)*pageSize 仍必须使用 long 计算，结果约为 4.6e18。
    assertThat(offsetValue).isInstanceOf(Long.class);
    assertThat(((Long) offsetValue).longValue())
        .isEqualTo((long) (Integer.MAX_VALUE - 1) * (long) Integer.MAX_VALUE);
  }

  private io.aegisops.workrecord.application.command.RecordQuery baseQuery(int page, int size) {
    return new io.aegisops.workrecord.application.command.RecordQuery(
        page,
        size,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null,
        List.of(),
        "recordTime",
        "desc",
        null,
        null,
        null);
  }

  // 防止 IDE 把 AppException/ErrorCode import 标记为 unused。
  @SuppressWarnings("unused")
  private AppException sampleException() {
    return new AppException(ErrorCode.PAGE_WINDOW_EXCEEDED, "x");
  }
}
