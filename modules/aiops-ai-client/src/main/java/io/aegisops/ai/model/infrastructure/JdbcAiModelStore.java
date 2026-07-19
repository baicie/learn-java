package io.aegisops.ai.model.infrastructure;

import io.aegisops.ai.model.ServerSideAiModelManagement;
import io.aegisops.ai.model.domain.AiModelConfig;
import io.aegisops.ai.model.port.AiModelStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ServerSideAiModelManagement
public class JdbcAiModelStore implements AiModelStore {
  private static final String COLUMNS =
      "id, tenant_id, provider, name, model_name, base_url, encrypted_api_key, enabled, "
          + "is_default, last_test_status, last_test_message, last_tested_at, created_at, updated_at";
  private final JdbcTemplate jdbc;

  public JdbcAiModelStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AiModelConfig> list(String tenantId) {
    return jdbc.query(
        "select "
            + COLUMNS
            + " from ai_model_config where tenant_id = ? order by is_default desc, updated_at desc",
        this::map,
        tenantId);
  }

  @Override
  public Optional<AiModelConfig> find(String tenantId, String id) {
    return jdbc
        .query(
            "select " + COLUMNS + " from ai_model_config where tenant_id = ? and id = ?",
            this::map,
            tenantId,
            id)
        .stream()
        .findFirst();
  }

  @Override
  public AiModelConfig insert(AiModelConfig model) {
    jdbc.update(
        """
        insert into ai_model_config(
          id, tenant_id, provider, name, model_name, base_url, encrypted_api_key,
          enabled, is_default, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        model.id(),
        model.tenantId(),
        model.provider(),
        model.name(),
        model.modelName(),
        model.baseUrl(),
        model.encryptedApiKey(),
        model.enabled(),
        model.defaultModel(),
        model.createdAt(),
        model.updatedAt());
    return find(model.tenantId(), model.id()).orElseThrow();
  }

  @Override
  public AiModelConfig update(AiModelConfig model) {
    jdbc.update(
        """
        update ai_model_config
        set name = ?, model_name = ?, base_url = ?, encrypted_api_key = ?, enabled = ?,
            is_default = ?, updated_at = ?
        where tenant_id = ? and id = ?
        """,
        model.name(),
        model.modelName(),
        model.baseUrl(),
        model.encryptedApiKey(),
        model.enabled(),
        model.defaultModel(),
        model.updatedAt(),
        model.tenantId(),
        model.id());
    return find(model.tenantId(), model.id()).orElseThrow();
  }

  @Override
  public void delete(String tenantId, String id) {
    jdbc.update("delete from ai_model_config where tenant_id = ? and id = ?", tenantId, id);
  }

  @Override
  public AiModelConfig setDefault(String tenantId, String id) {
    jdbc.update(
        "update ai_model_config set is_default = false, updated_at = now() where tenant_id = ? and is_default",
        tenantId);
    jdbc.update(
        "update ai_model_config set is_default = true, updated_at = now() where tenant_id = ? and id = ?",
        tenantId,
        id);
    return find(tenantId, id).orElseThrow();
  }

  @Override
  public AiModelConfig updateTestResult(String tenantId, String id, String status, String message) {
    jdbc.update(
        """
        update ai_model_config
        set last_test_status = ?, last_test_message = ?, last_tested_at = now(), updated_at = now()
        where tenant_id = ? and id = ?
        """,
        status,
        message,
        tenantId,
        id);
    return find(tenantId, id).orElseThrow();
  }

  private AiModelConfig map(ResultSet rs, int rowNum) throws SQLException {
    return new AiModelConfig(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("provider"),
        rs.getString("name"),
        rs.getString("model_name"),
        rs.getString("base_url"),
        rs.getString("encrypted_api_key"),
        rs.getBoolean("enabled"),
        rs.getBoolean("is_default"),
        rs.getString("last_test_status"),
        rs.getString("last_test_message"),
        rs.getObject("last_tested_at", OffsetDateTime.class),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }
}
