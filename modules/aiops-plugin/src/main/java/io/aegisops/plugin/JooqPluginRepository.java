package io.aegisops.plugin;

import static io.aegisops.persistence.AegisJooq.jsonbValue;
import static io.aegisops.persistence.jooq.public_.Tables.PLUGIN_DESCRIPTOR;
import static io.aegisops.persistence.jooq.public_.Tables.PLUGIN_EVENT;
import static io.aegisops.persistence.jooq.public_.Tables.TENANT_PLUGIN;
import static io.aegisops.persistence.jooq.public_.Tables.TENANT_PLUGIN_TOOL_POLICY;

import io.aegisops.plugin.dto.PluginDescriptorCreateCommand;
import io.aegisops.plugin.dto.PluginDescriptorRecord;
import io.aegisops.plugin.dto.PluginEventCommand;
import io.aegisops.plugin.dto.TenantPluginRecord;
import io.aegisops.plugin.dto.TenantPluginToolPolicyRecord;
import io.aegisops.plugin.dto.ToolPolicyCommand;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPluginRepository implements PluginRepository {
  private final DSLContext dsl;

  public JooqPluginRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public void upsertDescriptor(PluginDescriptorCreateCommand command) {
    dsl.insertInto(PLUGIN_DESCRIPTOR)
        .set(PLUGIN_DESCRIPTOR.ID, command.id())
        .set(PLUGIN_DESCRIPTOR.PLUGIN_KEY, command.pluginKey())
        .set(PLUGIN_DESCRIPTOR.NAME, command.name())
        .set(PLUGIN_DESCRIPTOR.VERSION, command.version())
        .set(PLUGIN_DESCRIPTOR.DESCRIPTION, command.description())
        .set(PLUGIN_DESCRIPTOR.PROVIDER, command.provider())
        .set(PLUGIN_DESCRIPTOR.STATUS, command.status())
        .set(PLUGIN_DESCRIPTOR.MANIFEST_JSON, jsonbValue(command.manifestJson()))
        .set(PLUGIN_DESCRIPTOR.CAPABILITIES_JSON, jsonbValue(command.capabilitiesJson()))
        .set(PLUGIN_DESCRIPTOR.CREATED_BY, command.createdBy())
        .set(PLUGIN_DESCRIPTOR.CREATED_AT, DSL.currentOffsetDateTime())
        .set(PLUGIN_DESCRIPTOR.UPDATED_AT, DSL.currentOffsetDateTime())
        .onConflict(PLUGIN_DESCRIPTOR.PLUGIN_KEY, PLUGIN_DESCRIPTOR.VERSION)
        .doUpdate()
        .set(PLUGIN_DESCRIPTOR.NAME, command.name())
        .set(PLUGIN_DESCRIPTOR.DESCRIPTION, command.description())
        .set(PLUGIN_DESCRIPTOR.PROVIDER, command.provider())
        .set(PLUGIN_DESCRIPTOR.STATUS, command.status())
        .set(PLUGIN_DESCRIPTOR.MANIFEST_JSON, jsonbValue(command.manifestJson()))
        .set(PLUGIN_DESCRIPTOR.CAPABILITIES_JSON, jsonbValue(command.capabilitiesJson()))
        .set(PLUGIN_DESCRIPTOR.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public Optional<PluginDescriptorRecord> findPlugin(String pluginId) {
    return dsl.select(
            PLUGIN_DESCRIPTOR.ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            PLUGIN_DESCRIPTOR.DESCRIPTION,
            PLUGIN_DESCRIPTOR.PROVIDER,
            PLUGIN_DESCRIPTOR.STATUS,
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("caps_json_string"),
            PLUGIN_DESCRIPTOR.CREATED_BY,
            PLUGIN_DESCRIPTOR.CREATED_AT,
            PLUGIN_DESCRIPTOR.UPDATED_AT)
        .from(PLUGIN_DESCRIPTOR)
        .where(PLUGIN_DESCRIPTOR.ID.eq(pluginId))
        .fetchOptional(this::toPluginRecord);
  }

  @Override
  public Optional<PluginDescriptorRecord> findPluginByKey(String pluginKey, String version) {
    return dsl.select(
            PLUGIN_DESCRIPTOR.ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            PLUGIN_DESCRIPTOR.DESCRIPTION,
            PLUGIN_DESCRIPTOR.PROVIDER,
            PLUGIN_DESCRIPTOR.STATUS,
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("caps_json_string"),
            PLUGIN_DESCRIPTOR.CREATED_BY,
            PLUGIN_DESCRIPTOR.CREATED_AT,
            PLUGIN_DESCRIPTOR.UPDATED_AT)
        .from(PLUGIN_DESCRIPTOR)
        .where(PLUGIN_DESCRIPTOR.PLUGIN_KEY.eq(pluginKey))
        .and(PLUGIN_DESCRIPTOR.VERSION.eq(version))
        .fetchOptional(this::toPluginRecord);
  }

  @Override
  public List<PluginDescriptorRecord> listPlugins() {
    return dsl.select(
            PLUGIN_DESCRIPTOR.ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            PLUGIN_DESCRIPTOR.DESCRIPTION,
            PLUGIN_DESCRIPTOR.PROVIDER,
            PLUGIN_DESCRIPTOR.STATUS,
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("caps_json_string"),
            PLUGIN_DESCRIPTOR.CREATED_BY,
            PLUGIN_DESCRIPTOR.CREATED_AT,
            PLUGIN_DESCRIPTOR.UPDATED_AT)
        .from(PLUGIN_DESCRIPTOR)
        .orderBy(PLUGIN_DESCRIPTOR.PLUGIN_KEY.asc(), PLUGIN_DESCRIPTOR.VERSION.desc())
        .fetch(this::toPluginRecord);
  }

  @Override
  public Optional<TenantPluginRecord> findTenantPlugin(String tenantId, String tenantPluginId) {
    return dsl.select(
            TENANT_PLUGIN.ID,
            TENANT_PLUGIN.TENANT_ID,
            TENANT_PLUGIN.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            TENANT_PLUGIN.STATUS,
            TENANT_PLUGIN.CONFIG_JSON.cast(String.class).as("config_json_string"),
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("caps_json_string"),
            TENANT_PLUGIN.ENABLED_BY,
            TENANT_PLUGIN.ENABLED_AT,
            TENANT_PLUGIN.DISABLED_BY,
            TENANT_PLUGIN.DISABLED_AT,
            TENANT_PLUGIN.CREATED_AT,
            TENANT_PLUGIN.UPDATED_AT)
        .from(TENANT_PLUGIN)
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN.PLUGIN_ID))
        .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
        .and(TENANT_PLUGIN.ID.eq(tenantPluginId))
        .fetchOptional(this::toTenantPluginRecord);
  }

  @Override
  public Optional<TenantPluginRecord> findTenantPluginByPluginId(String tenantId, String pluginId) {
    return dsl.select(
            TENANT_PLUGIN.ID,
            TENANT_PLUGIN.TENANT_ID,
            TENANT_PLUGIN.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            TENANT_PLUGIN.STATUS,
            TENANT_PLUGIN.CONFIG_JSON.cast(String.class).as("config_json_string"),
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("caps_json_string"),
            TENANT_PLUGIN.ENABLED_BY,
            TENANT_PLUGIN.ENABLED_AT,
            TENANT_PLUGIN.DISABLED_BY,
            TENANT_PLUGIN.DISABLED_AT,
            TENANT_PLUGIN.CREATED_AT,
            TENANT_PLUGIN.UPDATED_AT)
        .from(TENANT_PLUGIN)
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN.PLUGIN_ID))
        .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
        .and(TENANT_PLUGIN.PLUGIN_ID.eq(pluginId))
        .fetchOptional(this::toTenantPluginRecord);
  }

  @Override
  public List<TenantPluginRecord> listTenantPlugins(String tenantId) {
    return dsl.select(
            TENANT_PLUGIN.ID,
            TENANT_PLUGIN.TENANT_ID,
            TENANT_PLUGIN.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            PLUGIN_DESCRIPTOR.NAME,
            PLUGIN_DESCRIPTOR.VERSION,
            TENANT_PLUGIN.STATUS,
            TENANT_PLUGIN.CONFIG_JSON.cast(String.class).as("config_json_string"),
            PLUGIN_DESCRIPTOR.MANIFEST_JSON.cast(String.class).as("manifest_json_string"),
            PLUGIN_DESCRIPTOR.CAPABILITIES_JSON.cast(String.class).as("caps_json_string"),
            TENANT_PLUGIN.ENABLED_BY,
            TENANT_PLUGIN.ENABLED_AT,
            TENANT_PLUGIN.DISABLED_BY,
            TENANT_PLUGIN.DISABLED_AT,
            TENANT_PLUGIN.CREATED_AT,
            TENANT_PLUGIN.UPDATED_AT)
        .from(TENANT_PLUGIN)
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN.PLUGIN_ID))
        .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
        .orderBy(TENANT_PLUGIN.CREATED_AT.desc())
        .fetch(this::toTenantPluginRecord);
  }

  @Override
  public String enablePlugin(
      String tenantId, String pluginId, String configJson, String enabledBy) {
    Optional<TenantPluginRecord> existing = findTenantPluginByPluginId(tenantId, pluginId);
    if (existing.isPresent()) {
      String id = existing.get().id();
      dsl.update(TENANT_PLUGIN)
          .set(TENANT_PLUGIN.STATUS, "enabled")
          .set(TENANT_PLUGIN.CONFIG_JSON, jsonbValue(configJson))
          .set(TENANT_PLUGIN.ENABLED_BY, enabledBy)
          .set(TENANT_PLUGIN.ENABLED_AT, DSL.currentOffsetDateTime())
          .set(TENANT_PLUGIN.DISABLED_BY, (String) null)
          .set(TENANT_PLUGIN.DISABLED_AT, (java.time.OffsetDateTime) null)
          .set(TENANT_PLUGIN.UPDATED_AT, DSL.currentOffsetDateTime())
          .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
          .and(TENANT_PLUGIN.ID.eq(id))
          .execute();
      return id;
    }

    String id = "tplg_" + java.util.UUID.randomUUID().toString().replace("-", "");
    dsl.insertInto(TENANT_PLUGIN)
        .set(TENANT_PLUGIN.ID, id)
        .set(TENANT_PLUGIN.TENANT_ID, tenantId)
        .set(TENANT_PLUGIN.PLUGIN_ID, pluginId)
        .set(TENANT_PLUGIN.STATUS, "enabled")
        .set(TENANT_PLUGIN.CONFIG_JSON, jsonbValue(configJson))
        .set(TENANT_PLUGIN.ENABLED_BY, enabledBy)
        .set(TENANT_PLUGIN.ENABLED_AT, DSL.currentOffsetDateTime())
        .set(TENANT_PLUGIN.CREATED_AT, DSL.currentOffsetDateTime())
        .set(TENANT_PLUGIN.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
    return id;
  }

  @Override
  public boolean disablePlugin(String tenantId, String tenantPluginId, String disabledBy) {
    return dsl.update(TENANT_PLUGIN)
            .set(TENANT_PLUGIN.STATUS, "disabled")
            .set(TENANT_PLUGIN.DISABLED_BY, disabledBy)
            .set(TENANT_PLUGIN.DISABLED_AT, DSL.currentOffsetDateTime())
            .set(TENANT_PLUGIN.UPDATED_AT, DSL.currentOffsetDateTime())
            .where(TENANT_PLUGIN.TENANT_ID.eq(tenantId))
            .and(TENANT_PLUGIN.ID.eq(tenantPluginId))
            .and(TENANT_PLUGIN.STATUS.eq("enabled"))
            .execute()
        > 0;
  }

  @Override
  public List<TenantPluginToolPolicyRecord> listTenantToolPolicies(String tenantId) {
    return dsl.select(
            TENANT_PLUGIN_TOOL_POLICY.ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID,
            TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY,
            TENANT_PLUGIN_TOOL_POLICY.STATUS,
            TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL,
            TENANT_PLUGIN_TOOL_POLICY.CREATED_BY,
            TENANT_PLUGIN_TOOL_POLICY.CREATED_AT,
            TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT)
        .from(TENANT_PLUGIN_TOOL_POLICY)
        .join(TENANT_PLUGIN)
        .on(TENANT_PLUGIN.ID.eq(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID))
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID))
        .where(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID.eq(tenantId))
        .orderBy(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY.asc())
        .fetch(this::toToolPolicyRecord);
  }

  @Override
  public Optional<TenantPluginToolPolicyRecord> findAllowedToolPolicy(
      String tenantId, String toolKey) {
    return dsl.select(
            TENANT_PLUGIN_TOOL_POLICY.ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID,
            TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID,
            PLUGIN_DESCRIPTOR.PLUGIN_KEY,
            TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY,
            TENANT_PLUGIN_TOOL_POLICY.STATUS,
            TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL,
            TENANT_PLUGIN_TOOL_POLICY.CREATED_BY,
            TENANT_PLUGIN_TOOL_POLICY.CREATED_AT,
            TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT)
        .from(TENANT_PLUGIN_TOOL_POLICY)
        .join(TENANT_PLUGIN)
        .on(TENANT_PLUGIN.ID.eq(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID))
        .join(PLUGIN_DESCRIPTOR)
        .on(PLUGIN_DESCRIPTOR.ID.eq(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID))
        .where(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID.eq(tenantId))
        .and(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY.eq(toolKey))
        .and(TENANT_PLUGIN_TOOL_POLICY.STATUS.eq("allowed"))
        .and(TENANT_PLUGIN.STATUS.eq("enabled"))
        .and(PLUGIN_DESCRIPTOR.STATUS.eq("active"))
        .fetchOptional(this::toToolPolicyRecord);
  }

  @Override
  public void upsertToolPolicy(ToolPolicyCommand cmd) {
    dsl.insertInto(TENANT_PLUGIN_TOOL_POLICY)
        .set(TENANT_PLUGIN_TOOL_POLICY.ID, cmd.id())
        .set(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID, cmd.tenantId())
        .set(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID, cmd.tenantPluginId())
        .set(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID, cmd.pluginId())
        .set(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY, cmd.toolKey())
        .set(TENANT_PLUGIN_TOOL_POLICY.STATUS, cmd.status())
        .set(TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL, cmd.riskLevel())
        .set(TENANT_PLUGIN_TOOL_POLICY.CREATED_BY, cmd.createdBy())
        .set(TENANT_PLUGIN_TOOL_POLICY.CREATED_AT, DSL.currentOffsetDateTime())
        .set(TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .onConflict(
            TENANT_PLUGIN_TOOL_POLICY.TENANT_ID,
            TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID,
            TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY)
        .doUpdate()
        .set(TENANT_PLUGIN_TOOL_POLICY.STATUS, cmd.status())
        .set(TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL, cmd.riskLevel())
        .set(TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  @Override
  public void createEvent(PluginEventCommand cmd) {
    dsl.insertInto(PLUGIN_EVENT)
        .set(PLUGIN_EVENT.ID, cmd.id())
        .set(PLUGIN_EVENT.TENANT_ID, cmd.tenantId())
        .set(PLUGIN_EVENT.PLUGIN_ID, cmd.pluginId())
        .set(PLUGIN_EVENT.TENANT_PLUGIN_ID, cmd.tenantPluginId())
        .set(PLUGIN_EVENT.EVENT_TYPE, cmd.eventType())
        .set(PLUGIN_EVENT.SUMMARY, cmd.summary())
        .set(PLUGIN_EVENT.ACTOR, cmd.actor())
        .set(PLUGIN_EVENT.METADATA, jsonbValue(cmd.metadataJson()))
        .set(PLUGIN_EVENT.CREATED_AT, DSL.currentOffsetDateTime())
        .execute();
  }

  private PluginDescriptorRecord toPluginRecord(Record record) {
    return new PluginDescriptorRecord(
        record.get(PLUGIN_DESCRIPTOR.ID),
        record.get(PLUGIN_DESCRIPTOR.PLUGIN_KEY),
        record.get(PLUGIN_DESCRIPTOR.NAME),
        record.get(PLUGIN_DESCRIPTOR.VERSION),
        record.get(PLUGIN_DESCRIPTOR.DESCRIPTION),
        record.get(PLUGIN_DESCRIPTOR.PROVIDER),
        record.get(PLUGIN_DESCRIPTOR.STATUS),
        record.get("manifest_json_string", String.class),
        record.get("caps_json_string", String.class),
        record.get(PLUGIN_DESCRIPTOR.CREATED_BY),
        record.get(PLUGIN_DESCRIPTOR.CREATED_AT),
        record.get(PLUGIN_DESCRIPTOR.UPDATED_AT));
  }

  private TenantPluginRecord toTenantPluginRecord(Record record) {
    return new TenantPluginRecord(
        record.get(TENANT_PLUGIN.ID),
        record.get(TENANT_PLUGIN.TENANT_ID),
        record.get(TENANT_PLUGIN.PLUGIN_ID),
        record.get(PLUGIN_DESCRIPTOR.PLUGIN_KEY),
        record.get(PLUGIN_DESCRIPTOR.NAME),
        record.get(PLUGIN_DESCRIPTOR.VERSION),
        record.get(TENANT_PLUGIN.STATUS),
        record.get("config_json_string", String.class),
        record.get("manifest_json_string", String.class),
        record.get("caps_json_string", String.class),
        record.get(TENANT_PLUGIN.ENABLED_BY),
        record.get(TENANT_PLUGIN.ENABLED_AT),
        record.get(TENANT_PLUGIN.DISABLED_BY),
        record.get(TENANT_PLUGIN.DISABLED_AT),
        record.get(TENANT_PLUGIN.CREATED_AT),
        record.get(TENANT_PLUGIN.UPDATED_AT));
  }

  private TenantPluginToolPolicyRecord toToolPolicyRecord(Record record) {
    return new TenantPluginToolPolicyRecord(
        record.get(TENANT_PLUGIN_TOOL_POLICY.ID),
        record.get(TENANT_PLUGIN_TOOL_POLICY.TENANT_ID),
        record.get(TENANT_PLUGIN_TOOL_POLICY.TENANT_PLUGIN_ID),
        record.get(TENANT_PLUGIN_TOOL_POLICY.PLUGIN_ID),
        record.get(PLUGIN_DESCRIPTOR.PLUGIN_KEY),
        record.get(TENANT_PLUGIN_TOOL_POLICY.TOOL_KEY),
        record.get(TENANT_PLUGIN_TOOL_POLICY.STATUS),
        record.get(TENANT_PLUGIN_TOOL_POLICY.RISK_LEVEL),
        record.get(TENANT_PLUGIN_TOOL_POLICY.CREATED_BY),
        record.get(TENANT_PLUGIN_TOOL_POLICY.CREATED_AT),
        record.get(TENANT_PLUGIN_TOOL_POLICY.UPDATED_AT));
  }
}
