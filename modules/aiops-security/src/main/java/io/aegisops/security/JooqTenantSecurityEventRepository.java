package io.aegisops.security;

import io.aegisops.persistence.AegisJooq;
import io.aegisops.persistence.jooq.Tables;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqTenantSecurityEventRepository implements TenantSecurityEventRepository {
  private final DSLContext dsl;

  public JooqTenantSecurityEventRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void create(TenantSecurityEventCreateCommand command) {
    dsl.insertInto(Tables.TENANT_SECURITY_EVENT)
        .set(Tables.TENANT_SECURITY_EVENT.ID, command.id())
        .set(Tables.TENANT_SECURITY_EVENT.TENANT_ID, command.tenantId())
        .set(Tables.TENANT_SECURITY_EVENT.EVENT_TYPE, command.eventType())
        .set(Tables.TENANT_SECURITY_EVENT.SEVERITY, command.severity())
        .set(Tables.TENANT_SECURITY_EVENT.ACTOR, command.actor())
        .set(Tables.TENANT_SECURITY_EVENT.REQUEST_PATH, command.requestPath())
        .set(Tables.TENANT_SECURITY_EVENT.REMOTE_ADDR, command.remoteAddr())
        .set(Tables.TENANT_SECURITY_EVENT.SUMMARY, command.summary())
        .set(Tables.TENANT_SECURITY_EVENT.METADATA, AegisJooq.jsonbValue(command.metadataJson()))
        .set(Tables.TENANT_SECURITY_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }
}
