package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.aegisops.workrecord.application.port.WorkRecordUserPort;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WorkRecordUserLookupServiceTest {
  private final WorkRecordUserPort users = mock(WorkRecordUserPort.class);
  private final WorkRecordUserLookupService service = new WorkRecordUserLookupService(users);

  @Test
  void resolvesDistinctDisplayNamesWithinTenant() {
    when(users.displayNames(eq("tenant-1"), anyCollection()))
        .thenReturn(Map.of("u1", "张三", "u2", "李四"));

    assertThat(service.displayNames("tenant-1", java.util.List.of("u1", "u1", " u2 ")))
        .containsExactlyInAnyOrderEntriesOf(Map.of("u1", "张三", "u2", "李四"));
  }

  @Test
  void rejectsOversizedLookup() {
    var ids = IntStream.range(0, 101).mapToObj(index -> "u" + index).toList();

    assertThatThrownBy(() -> service.displayNames("tenant-1", ids))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
