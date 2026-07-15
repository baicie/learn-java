package io.aegisops.platform.iam.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.common.tenant.TenantContext;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PlatformIamRepositoryPostgresIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static JdbcTemplate jdbc;
  static JdbcPlatformRoleRepository roles;
  static JdbcPlatformUserRepository users;

  @BeforeAll
  static void setUp() {
    PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUser(POSTGRES.getUsername());
    dataSource.setPassword(POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    roles = new JdbcPlatformRoleRepository(jdbc);
    users = new JdbcPlatformUserRepository(jdbc);

    jdbc.execute("create schema iam");
    jdbc.execute(
        """
        create table tenant(id varchar(64) primary key);
        create table sys_user(
          id varchar(64) primary key, tenant_id varchar(64) not null,
          username varchar(64) not null, display_name varchar(128) not null,
          email varchar(128), password_hash varchar(255) not null,
          status varchar(32) not null, failed_login_count integer not null default 0,
          locked_until timestamptz, last_login_at timestamptz,
          password_changed_at timestamptz, row_version integer not null default 1,
          created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
          deleted_at timestamptz);
        create table iam.permission(permission_code varchar(128) primary key);
        create table iam.role_definition(
          tenant_id varchar(64) not null, role_code varchar(64) not null,
          role_name varchar(128) not null, description varchar(512),
          system_builtin boolean not null, enabled boolean not null,
          row_version integer not null default 1, deleted_at timestamptz,
          created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
          primary key(tenant_id, role_code));
        create table iam.role_permission(
          tenant_id varchar(64) not null, role_code varchar(64) not null,
          permission_code varchar(128) not null, created_at timestamptz not null default now(),
          primary key(tenant_id, role_code, permission_code));
        create table iam.role_data_scope_v2(
          tenant_id varchar(64) not null, role_code varchar(64) not null,
          resource_code varchar(64) not null, scope_type varchar(32) not null,
          scope_json jsonb not null default '{}'::jsonb,
          created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
          primary key(tenant_id, role_code, resource_code));
        create table iam.user_role(
          tenant_id varchar(64) not null, user_id varchar(64) not null,
          role_code varchar(64) not null, created_at timestamptz not null default now(),
          created_by varchar(64) not null, primary key(tenant_id, user_id, role_code));
        """);
    jdbc.update("insert into tenant(id) values ('t1'), ('t2')");
    jdbc.update("insert into iam.permission(permission_code) values ('read'), ('write')");
    jdbc.update(
        """
        insert into iam.role_definition(
          tenant_id, role_code, role_name, system_builtin, enabled)
        values ('t1', 'operator', 'T1 Operator', false, true),
               ('t2', 'operator', 'T2 Operator', false, true)
        """);
    jdbc.update(
        """
        insert into sys_user(id, tenant_id, username, display_name, password_hash, status)
        values ('u1', 't1', 'alice', 'Alice', 'hash', 'active'),
               ('u2', 't2', 'alice', 'Alice T2', 'hash', 'active')
        """);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void rolesAndPermissionsRemainTenantScoped() {
    TenantContext.setTenantId("t1");
    roles.replacePermissions("operator", Set.of("read"));
    assertThat(roles.findByCode("operator").orElseThrow().permissions()).containsExactly("read");

    TenantContext.setTenantId("t2");
    roles.replacePermissions("operator", Set.of("write"));
    assertThat(roles.findByCode("operator").orElseThrow().permissions()).containsExactly("write");

    TenantContext.setTenantId("t1");
    assertThat(roles.findByCode("operator").orElseThrow().permissions()).containsExactly("read");
  }

  @Test
  void usersAndRoleAssignmentsRemainTenantScoped() {
    TenantContext.setTenantId("t1");
    users.replaceRoles("t1", "u1", java.util.List.of("operator"), java.time.OffsetDateTime.now());

    assertThat(users.findById("u1").orElseThrow().roles())
        .extracting(role -> role.code())
        .containsExactly("operator");
    assertThat(users.findById("u2")).isEmpty();

    TenantContext.setTenantId("t2");
    assertThat(users.findById("u1")).isEmpty();
    assertThat(users.findById("u2").orElseThrow().roles()).isEmpty();
  }
}
