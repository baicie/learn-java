package io.aegisops.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PlatformMenuRepositoryTest {

  @Test
  void listEnabledShouldUseDynamicInClauseForAuthorities() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

    PlatformMenuRepository repository = new PlatformMenuRepository(jdbc);
    repository.listEnabled(List.of("incident:read", "audit:read"));

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);

    verify(jdbc).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());

    String sql = sqlCaptor.getValue();
    Object[] args = argsCaptor.getValue();

    assertThat(sql)
        .contains("permission_code in")
        .contains("?, ?")
        .contains("order by sort_order asc");
    assertThat(args).containsExactly("incident:read", "audit:read");
  }

  @Test
  void listEnabledShouldOnlyReturnPublicMenusWhenAuthoritiesEmpty() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

    PlatformMenuRepository repository = new PlatformMenuRepository(jdbc);
    repository.listEnabled(List.of());

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

    verify(jdbc).query(sqlCaptor.capture(), any(RowMapper.class));

    String sql = sqlCaptor.getValue();
    assertThat(sql)
        .contains("permission_code is null")
        .contains("permission_code = ''")
        .doesNotContain("permission_code in");
  }
}
