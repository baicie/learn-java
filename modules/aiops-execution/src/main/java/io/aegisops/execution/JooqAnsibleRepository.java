package io.aegisops.execution;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.Tables.ANSIBLE_EXECUTION_POLICY;
import static io.aegisops.persistence.jooq.Tables.ANSIBLE_INVENTORY;
import static io.aegisops.persistence.jooq.Tables.ANSIBLE_PLAYBOOK;

import io.aegisops.execution.dto.AnsibleInventoryCreateCommand;
import io.aegisops.execution.dto.AnsibleInventoryRecord;
import io.aegisops.execution.dto.AnsiblePlaybookCreateCommand;
import io.aegisops.execution.dto.AnsiblePlaybookRecord;
import io.aegisops.execution.dto.AnsiblePolicyCreateCommand;
import io.aegisops.execution.dto.AnsiblePolicyRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAnsibleRepository implements AnsibleRepository {
  private final DSLContext dsl;

  public JooqAnsibleRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void createInventory(AnsibleInventoryCreateCommand command) {
    dsl.insertInto(ANSIBLE_INVENTORY)
        .set(ANSIBLE_INVENTORY.ID, command.id())
        .set(ANSIBLE_INVENTORY.TENANT_ID, command.tenantId())
        .set(ANSIBLE_INVENTORY.NAME, command.name())
        .set(ANSIBLE_INVENTORY.DESCRIPTION, command.description())
        .set(ANSIBLE_INVENTORY.INVENTORY_TYPE, command.inventoryType())
        .set(ANSIBLE_INVENTORY.INLINE_INVENTORY, command.inlineInventory())
        .set(ANSIBLE_INVENTORY.FILE_REF, command.fileRef())
        .set(ANSIBLE_INVENTORY.ENABLED, command.enabled())
        .set(ANSIBLE_INVENTORY.CREATED_BY, command.createdBy())
        .set(ANSIBLE_INVENTORY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ANSIBLE_INVENTORY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<AnsibleInventoryRecord> listInventories(String tenantId, boolean includeDisabled) {
    Condition condition = ANSIBLE_INVENTORY.TENANT_ID.eq(tenantId);
    if (!includeDisabled) {
      condition = condition.and(ANSIBLE_INVENTORY.ENABLED.isTrue());
    }

    return dsl.select(
            ANSIBLE_INVENTORY.ID,
            ANSIBLE_INVENTORY.TENANT_ID,
            ANSIBLE_INVENTORY.NAME,
            ANSIBLE_INVENTORY.DESCRIPTION,
            ANSIBLE_INVENTORY.INVENTORY_TYPE,
            ANSIBLE_INVENTORY.INLINE_INVENTORY,
            ANSIBLE_INVENTORY.FILE_REF,
            ANSIBLE_INVENTORY.ENABLED,
            ANSIBLE_INVENTORY.CREATED_BY,
            ANSIBLE_INVENTORY.CREATED_AT,
            ANSIBLE_INVENTORY.UPDATED_AT)
        .from(ANSIBLE_INVENTORY)
        .where(condition)
        .orderBy(ANSIBLE_INVENTORY.CREATED_AT.desc())
        .fetch(this::toInventoryRecord);
  }

  @Override
  public Optional<AnsibleInventoryRecord> findInventory(String tenantId, String inventoryId) {
    return dsl.select(
            ANSIBLE_INVENTORY.ID,
            ANSIBLE_INVENTORY.TENANT_ID,
            ANSIBLE_INVENTORY.NAME,
            ANSIBLE_INVENTORY.DESCRIPTION,
            ANSIBLE_INVENTORY.INVENTORY_TYPE,
            ANSIBLE_INVENTORY.INLINE_INVENTORY,
            ANSIBLE_INVENTORY.FILE_REF,
            ANSIBLE_INVENTORY.ENABLED,
            ANSIBLE_INVENTORY.CREATED_BY,
            ANSIBLE_INVENTORY.CREATED_AT,
            ANSIBLE_INVENTORY.UPDATED_AT)
        .from(ANSIBLE_INVENTORY)
        .where(ANSIBLE_INVENTORY.TENANT_ID.eq(tenantId))
        .and(ANSIBLE_INVENTORY.ID.eq(inventoryId))
        .fetchOptional(this::toInventoryRecord);
  }

  @Override
  public boolean setInventoryEnabled(String tenantId, String inventoryId, boolean enabled) {
    return dsl.update(ANSIBLE_INVENTORY)
            .set(ANSIBLE_INVENTORY.ENABLED, enabled)
            .set(ANSIBLE_INVENTORY.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ANSIBLE_INVENTORY.TENANT_ID.eq(tenantId))
            .and(ANSIBLE_INVENTORY.ID.eq(inventoryId))
            .execute()
        > 0;
  }

  @Override
  public void createPlaybook(AnsiblePlaybookCreateCommand command) {
    dsl.insertInto(ANSIBLE_PLAYBOOK)
        .set(ANSIBLE_PLAYBOOK.ID, command.id())
        .set(ANSIBLE_PLAYBOOK.TENANT_ID, command.tenantId())
        .set(ANSIBLE_PLAYBOOK.NAME, command.name())
        .set(ANSIBLE_PLAYBOOK.DESCRIPTION, command.description())
        .set(ANSIBLE_PLAYBOOK.PLAYBOOK_REF, command.playbookRef())
        .set(ANSIBLE_PLAYBOOK.PLAYBOOK_CONTENT, command.playbookContent())
        .set(ANSIBLE_PLAYBOOK.VARIABLES_SCHEMA, jsonbValue(command.variablesSchemaJson()))
        .set(ANSIBLE_PLAYBOOK.ALLOWED_TAGS, jsonbValue(command.allowedTagsJson()))
        .set(ANSIBLE_PLAYBOOK.ENABLED, command.enabled())
        .set(ANSIBLE_PLAYBOOK.CREATED_BY, command.createdBy())
        .set(ANSIBLE_PLAYBOOK.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ANSIBLE_PLAYBOOK.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createPolicy(AnsiblePolicyCreateCommand command) {
    dsl.insertInto(ANSIBLE_EXECUTION_POLICY)
        .set(ANSIBLE_EXECUTION_POLICY.ID, command.id())
        .set(ANSIBLE_EXECUTION_POLICY.TENANT_ID, command.tenantId())
        .set(ANSIBLE_EXECUTION_POLICY.PLAYBOOK_ID, command.playbookId())
        .set(ANSIBLE_EXECUTION_POLICY.ALLOW_LIVE, command.allowLive())
        .set(ANSIBLE_EXECUTION_POLICY.DEFAULT_CHECK_MODE, command.defaultCheckMode())
        .set(
            ANSIBLE_EXECUTION_POLICY.ALLOWED_INVENTORY_IDS,
            jsonbValue(command.allowedInventoryIdsJson()))
        .set(
            ANSIBLE_EXECUTION_POLICY.ALLOWED_EXTRA_VARS, jsonbValue(command.allowedExtraVarsJson()))
        .set(ANSIBLE_EXECUTION_POLICY.MAX_EXTRA_VARS_BYTES, command.maxExtraVarsBytes())
        .set(ANSIBLE_EXECUTION_POLICY.TIMEOUT_SECONDS, command.timeoutSeconds())
        .set(ANSIBLE_EXECUTION_POLICY.ENABLED, command.enabled())
        .set(ANSIBLE_EXECUTION_POLICY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(ANSIBLE_EXECUTION_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public List<AnsiblePlaybookRecord> listPlaybooks(String tenantId, boolean includeDisabled) {
    Condition condition = ANSIBLE_PLAYBOOK.TENANT_ID.eq(tenantId);
    if (!includeDisabled) {
      condition = condition.and(ANSIBLE_PLAYBOOK.ENABLED.isTrue());
    }

    return dsl.select(
            ANSIBLE_PLAYBOOK.ID,
            ANSIBLE_PLAYBOOK.TENANT_ID,
            ANSIBLE_PLAYBOOK.NAME,
            ANSIBLE_PLAYBOOK.DESCRIPTION,
            ANSIBLE_PLAYBOOK.PLAYBOOK_REF,
            ANSIBLE_PLAYBOOK.PLAYBOOK_CONTENT,
            ANSIBLE_PLAYBOOK.VARIABLES_SCHEMA.cast(String.class).as("variables_schema_json"),
            ANSIBLE_PLAYBOOK.ALLOWED_TAGS.cast(String.class).as("allowed_tags_json"),
            ANSIBLE_PLAYBOOK.ENABLED,
            ANSIBLE_PLAYBOOK.CREATED_BY,
            ANSIBLE_PLAYBOOK.CREATED_AT,
            ANSIBLE_PLAYBOOK.UPDATED_AT)
        .from(ANSIBLE_PLAYBOOK)
        .where(condition)
        .orderBy(ANSIBLE_PLAYBOOK.CREATED_AT.desc())
        .fetch(this::toPlaybookRecord);
  }

  @Override
  public Optional<AnsiblePlaybookRecord> findPlaybook(String tenantId, String playbookId) {
    return dsl.select(
            ANSIBLE_PLAYBOOK.ID,
            ANSIBLE_PLAYBOOK.TENANT_ID,
            ANSIBLE_PLAYBOOK.NAME,
            ANSIBLE_PLAYBOOK.DESCRIPTION,
            ANSIBLE_PLAYBOOK.PLAYBOOK_REF,
            ANSIBLE_PLAYBOOK.PLAYBOOK_CONTENT,
            ANSIBLE_PLAYBOOK.VARIABLES_SCHEMA.cast(String.class).as("variables_schema_json"),
            ANSIBLE_PLAYBOOK.ALLOWED_TAGS.cast(String.class).as("allowed_tags_json"),
            ANSIBLE_PLAYBOOK.ENABLED,
            ANSIBLE_PLAYBOOK.CREATED_BY,
            ANSIBLE_PLAYBOOK.CREATED_AT,
            ANSIBLE_PLAYBOOK.UPDATED_AT)
        .from(ANSIBLE_PLAYBOOK)
        .where(ANSIBLE_PLAYBOOK.TENANT_ID.eq(tenantId))
        .and(ANSIBLE_PLAYBOOK.ID.eq(playbookId))
        .fetchOptional(this::toPlaybookRecord);
  }

  @Override
  public Optional<AnsiblePolicyRecord> findPolicy(String tenantId, String playbookId) {
    return dsl.select(
            ANSIBLE_EXECUTION_POLICY.ID,
            ANSIBLE_EXECUTION_POLICY.TENANT_ID,
            ANSIBLE_EXECUTION_POLICY.PLAYBOOK_ID,
            ANSIBLE_EXECUTION_POLICY.ALLOW_LIVE,
            ANSIBLE_EXECUTION_POLICY.DEFAULT_CHECK_MODE,
            ANSIBLE_EXECUTION_POLICY
                .ALLOWED_INVENTORY_IDS
                .cast(String.class)
                .as("allowed_inventory_ids_json"),
            ANSIBLE_EXECUTION_POLICY
                .ALLOWED_EXTRA_VARS
                .cast(String.class)
                .as("allowed_extra_vars_json"),
            ANSIBLE_EXECUTION_POLICY.MAX_EXTRA_VARS_BYTES,
            ANSIBLE_EXECUTION_POLICY.TIMEOUT_SECONDS,
            ANSIBLE_EXECUTION_POLICY.ENABLED,
            ANSIBLE_EXECUTION_POLICY.CREATED_AT,
            ANSIBLE_EXECUTION_POLICY.UPDATED_AT)
        .from(ANSIBLE_EXECUTION_POLICY)
        .where(ANSIBLE_EXECUTION_POLICY.TENANT_ID.eq(tenantId))
        .and(ANSIBLE_EXECUTION_POLICY.PLAYBOOK_ID.eq(playbookId))
        .fetchOptional(this::toPolicyRecord);
  }

  @Override
  public boolean setPlaybookEnabled(String tenantId, String playbookId, boolean enabled) {
    return dsl.update(ANSIBLE_PLAYBOOK)
            .set(ANSIBLE_PLAYBOOK.ENABLED, enabled)
            .set(ANSIBLE_PLAYBOOK.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(ANSIBLE_PLAYBOOK.TENANT_ID.eq(tenantId))
            .and(ANSIBLE_PLAYBOOK.ID.eq(playbookId))
            .execute()
        > 0;
  }

  private AnsibleInventoryRecord toInventoryRecord(org.jooq.Record record) {
    return new AnsibleInventoryRecord(
        record.get(ANSIBLE_INVENTORY.ID),
        record.get(ANSIBLE_INVENTORY.TENANT_ID),
        record.get(ANSIBLE_INVENTORY.NAME),
        record.get(ANSIBLE_INVENTORY.DESCRIPTION),
        record.get(ANSIBLE_INVENTORY.INVENTORY_TYPE),
        record.get(ANSIBLE_INVENTORY.INLINE_INVENTORY),
        record.get(ANSIBLE_INVENTORY.FILE_REF),
        Boolean.TRUE.equals(record.get(ANSIBLE_INVENTORY.ENABLED)),
        record.get(ANSIBLE_INVENTORY.CREATED_BY),
        record.get(ANSIBLE_INVENTORY.CREATED_AT),
        record.get(ANSIBLE_INVENTORY.UPDATED_AT));
  }

  private AnsiblePlaybookRecord toPlaybookRecord(org.jooq.Record record) {
    return new AnsiblePlaybookRecord(
        record.get(ANSIBLE_PLAYBOOK.ID),
        record.get(ANSIBLE_PLAYBOOK.TENANT_ID),
        record.get(ANSIBLE_PLAYBOOK.NAME),
        record.get(ANSIBLE_PLAYBOOK.DESCRIPTION),
        record.get(ANSIBLE_PLAYBOOK.PLAYBOOK_REF),
        record.get(ANSIBLE_PLAYBOOK.PLAYBOOK_CONTENT),
        record.get("variables_schema_json", String.class),
        record.get("allowed_tags_json", String.class),
        Boolean.TRUE.equals(record.get(ANSIBLE_PLAYBOOK.ENABLED)),
        record.get(ANSIBLE_PLAYBOOK.CREATED_BY),
        record.get(ANSIBLE_PLAYBOOK.CREATED_AT),
        record.get(ANSIBLE_PLAYBOOK.UPDATED_AT));
  }

  private AnsiblePolicyRecord toPolicyRecord(org.jooq.Record record) {
    return new AnsiblePolicyRecord(
        record.get(ANSIBLE_EXECUTION_POLICY.ID),
        record.get(ANSIBLE_EXECUTION_POLICY.TENANT_ID),
        record.get(ANSIBLE_EXECUTION_POLICY.PLAYBOOK_ID),
        Boolean.TRUE.equals(record.get(ANSIBLE_EXECUTION_POLICY.ALLOW_LIVE)),
        Boolean.TRUE.equals(record.get(ANSIBLE_EXECUTION_POLICY.DEFAULT_CHECK_MODE)),
        record.get("allowed_inventory_ids_json", String.class),
        record.get("allowed_extra_vars_json", String.class),
        value(record.get(ANSIBLE_EXECUTION_POLICY.MAX_EXTRA_VARS_BYTES)),
        value(record.get(ANSIBLE_EXECUTION_POLICY.TIMEOUT_SECONDS)),
        Boolean.TRUE.equals(record.get(ANSIBLE_EXECUTION_POLICY.ENABLED)),
        record.get(ANSIBLE_EXECUTION_POLICY.CREATED_AT),
        record.get(ANSIBLE_EXECUTION_POLICY.UPDATED_AT));
  }

  private int value(Integer value) {
    return value == null ? 0 : value;
  }
}
