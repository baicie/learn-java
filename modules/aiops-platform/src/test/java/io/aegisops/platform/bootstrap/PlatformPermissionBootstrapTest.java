package io.aegisops.platform.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class PlatformPermissionBootstrapTest {

  @Test
  void shouldExposeRequiredPermissionCodes() {
    PlatformPermissionBootstrap bootstrap =
        new PlatformPermissionBootstrap(Mockito.mock(JdbcTemplate.class));

    assertThat(bootstrap.permissions())
        .extracting(PlatformPermissionCode::permissionCode)
        .contains(
            "platform:dict:read",
            "platform:dict:write",
            "platform:calendar:read",
            "platform:calendar:write",
            "platform:calendar:import",
            "work-record:template:read",
            "work-record:template:write",
            "work-record:read:self",
            "work-record:read:all",
            "work-record:write",
            "work-record:delete",
            "work-record:export");
  }

  @Test
  void shouldExposeDefaultRoleGrants() {
    PlatformPermissionBootstrap bootstrap =
        new PlatformPermissionBootstrap(Mockito.mock(JdbcTemplate.class));

    assertThat(bootstrap.grants())
        .extracting(PlatformDefaultRoleGrant::roleCode)
        .contains("system_admin", "record_admin", "normal_user", "readonly_user");
  }

  @Test
  void shouldWriteSeedData() {
    JdbcTemplate jdbc = Mockito.mock(JdbcTemplate.class);
    PlatformPermissionBootstrap bootstrap = new PlatformPermissionBootstrap(jdbc);

    bootstrap.initialize();

    verify(jdbc, atLeastOnce()).update(Mockito.anyString(), Mockito.any(Object[].class));
  }
}
