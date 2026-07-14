package io.aegisops.platform.bootstrap;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PlatformPermissionBootstrap implements ApplicationRunner {
  private final JdbcTemplate jdbc;

  public PlatformPermissionBootstrap(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void run(ApplicationArguments args) {
    initialize();
  }

  @Transactional
  public void initialize() {
    for (PlatformPermissionCode permission : permissions()) {
      jdbc.update(
          """
              insert into platform_permission_code(
                id, permission_code, permission_name, module_code, description, sort_order)
              values (?, ?, ?, ?, ?, ?)
              on conflict (permission_code) do update
              set permission_name = excluded.permission_name,
                  module_code = excluded.module_code,
                  description = excluded.description,
                  sort_order = excluded.sort_order,
                  enabled = true,
                  updated_at = now()
              """,
          permission.id(),
          permission.permissionCode(),
          permission.permissionName(),
          permission.moduleCode(),
          permission.description(),
          permission.sortOrder());
    }

    for (PlatformDefaultRoleGrant grant : grants()) {
      jdbc.update(
          """
              insert into platform_default_role_grant(id, role_code, permission_code)
              values (?, ?, ?)
              on conflict (role_code, permission_code) do update
              set enabled = true
              """,
          grant.id(),
          grant.roleCode(),
          grant.permissionCode());
    }
  }

  public List<PlatformPermissionCode> permissions() {
    List<PlatformPermissionCode> permissions = new ArrayList<>(platformPermissions());
    permissions.addAll(workRecordPermissions());
    return List.copyOf(permissions);
  }

  private List<PlatformPermissionCode> platformPermissions() {
    return List.of(
        new PlatformPermissionCode(
            "perm-platform-dict-read",
            "platform:dict:read",
            "查看字典",
            "platform",
            "查看平台字典类型和字典项",
            100),
        new PlatformPermissionCode(
            "perm-platform-dict-write",
            "platform:dict:write",
            "维护字典",
            "platform",
            "新增、编辑、禁用字典类型和字典项",
            110),
        new PlatformPermissionCode(
            "perm-platform-calendar-read",
            "platform:calendar:read",
            "查看工作日历",
            "platform",
            "查看工作日历和日期",
            120),
        new PlatformPermissionCode(
            "perm-platform-calendar-write",
            "platform:calendar:write",
            "维护工作日历",
            "platform",
            "新增日历、覆盖日期",
            130),
        new PlatformPermissionCode(
            "perm-platform-calendar-import",
            "platform:calendar:import",
            "导入工作日历",
            "platform",
            "导入 CSV 工作日历",
            140));
  }

  private List<PlatformPermissionCode> workRecordPermissions() {
    return List.of(
        new PlatformPermissionCode(
            "perm-work-record-template-read",
            "work-record:template:read",
            "查看工作记录模板",
            "work-record",
            "查看工作记录模板",
            200),
        new PlatformPermissionCode(
            "perm-work-record-template-write",
            "work-record:template:write",
            "维护工作记录模板",
            "work-record",
            "维护工作记录模板",
            210),
        new PlatformPermissionCode(
            "perm-work-record-read-self",
            "work-record:read:self",
            "查看自己的工作记录",
            "work-record",
            "查看自己创建或负责的记录",
            220),
        new PlatformPermissionCode(
            "perm-work-record-read-all",
            "work-record:read:all",
            "查看全部工作记录",
            "work-record",
            "查看全部工作记录",
            230),
        new PlatformPermissionCode(
            "perm-work-record-write",
            "work-record:write",
            "维护工作记录",
            "work-record",
            "新增和编辑工作记录",
            240),
        new PlatformPermissionCode(
            "perm-work-record-delete",
            "work-record:delete",
            "删除工作记录",
            "work-record",
            "软删除工作记录",
            250),
        new PlatformPermissionCode(
            "perm-work-record-export",
            "work-record:export",
            "导出工作记录",
            "work-record",
            "导出工作记录",
            260));
  }

  public List<PlatformDefaultRoleGrant> grants() {
    return List.of(
        new PlatformDefaultRoleGrant(
            "grant-system-admin-platform-dict-read", "system_admin", "platform:dict:read"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-platform-dict-write", "system_admin", "platform:dict:write"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-platform-calendar-read", "system_admin", "platform:calendar:read"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-platform-calendar-write",
            "system_admin",
            "platform:calendar:write"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-platform-calendar-import",
            "system_admin",
            "platform:calendar:import"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-template-read",
            "system_admin",
            "work-record:template:read"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-template-write",
            "system_admin",
            "work-record:template:write"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-read-self", "system_admin", "work-record:read:self"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-read-all", "system_admin", "work-record:read:all"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-write", "system_admin", "work-record:write"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-delete", "system_admin", "work-record:delete"),
        new PlatformDefaultRoleGrant(
            "grant-system-admin-work-record-export", "system_admin", "work-record:export"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-platform-dict-read", "record_admin", "platform:dict:read"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-platform-calendar-read", "record_admin", "platform:calendar:read"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-work-record-template-read",
            "record_admin",
            "work-record:template:read"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-work-record-template-write",
            "record_admin",
            "work-record:template:write"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-work-record-read-all", "record_admin", "work-record:read:all"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-work-record-write", "record_admin", "work-record:write"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-work-record-delete", "record_admin", "work-record:delete"),
        new PlatformDefaultRoleGrant(
            "grant-record-admin-work-record-export", "record_admin", "work-record:export"),
        new PlatformDefaultRoleGrant(
            "grant-normal-user-work-record-read-self", "normal_user", "work-record:read:self"),
        new PlatformDefaultRoleGrant(
            "grant-normal-user-work-record-write", "normal_user", "work-record:write"),
        new PlatformDefaultRoleGrant(
            "grant-readonly-user-work-record-read-self", "readonly_user", "work-record:read:self"));
  }
}
