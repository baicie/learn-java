package io.aegisops.platform.dictionary;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 默认实现：从 {@code tenant} 表读取 active 租户。 */
@Component
public class DefaultDictionarySupportImpl implements DefaultDictionarySupport {

  private final JdbcTemplate jdbc;

  public DefaultDictionarySupportImpl(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<String> listActiveTenants() {
    return jdbc.query(
        "select id from tenant where status = 'active'", (rs, rowNum) -> rs.getString("id"));
  }
}
