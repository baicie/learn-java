package io.aegisops.platform.dictionary;

import io.aegisops.common.id.Ids;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DictionaryRepository {
  private final JdbcTemplate jdbc;

  public DictionaryRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<DictTypeRecord> listTypes(String tenantId) {
    return listTypes(tenantId, false);
  }

  public List<DictTypeRecord> listTypes(String tenantId, boolean includeDisabled) {
    return jdbc.query(
        """
            select id, tenant_id, dict_code, dict_name, description, system_builtin,
                   enabled, sort_order, created_by, created_at, updated_at
            from platform_dict_type
            where tenant_id = ?
              and (? = true or enabled = true)
            order by sort_order asc, created_at asc
            """,
        (rs, rowNum) -> mapType(rs),
        tenantId,
        includeDisabled);
  }

  public Optional<DictTypeRecord> findType(String tenantId, String dictCode) {
    List<DictTypeRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, dict_code, dict_name, description, system_builtin,
                       enabled, sort_order, created_by, created_at, updated_at
                from platform_dict_type
                where tenant_id = ? and dict_code = ?
                """,
            (rs, rowNum) -> mapType(rs),
            tenantId,
            dictCode);
    return rows.stream().findFirst();
  }

  public Optional<DictTypeRecord> findTypeById(String tenantId, String id) {
    List<DictTypeRecord> rows =
        jdbc.query(
            """
                select id, tenant_id, dict_code, dict_name, description, system_builtin,
                       enabled, sort_order, created_by, created_at, updated_at
                from platform_dict_type
                where tenant_id = ? and id = ?
                """,
            (rs, rowNum) -> mapType(rs),
            tenantId,
            id);
    return rows.stream().findFirst();
  }

  public DictTypeRecord createType(
      String tenantId, CreateDictTypeRequest request, String createdBy) {
    String id = Ids.newId();
    jdbc.update(
        """
            insert into platform_dict_type(
              id, tenant_id, dict_code, dict_name, description, enabled, sort_order, created_by)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """,
        id,
        tenantId,
        request.dictCode(),
        request.dictName(),
        request.description(),
        request.enabled() == null || request.enabled(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        createdBy);
    return findType(tenantId, request.dictCode()).orElseThrow();
  }

  /** 字典类型按业务惯例采用软删除（enabled=false），避免被业务侧硬删除造成历史记录不可解释。 内置字典不允许禁用，但可以更新元数据。 */
  public Optional<DictTypeRecord> updateType(
      String tenantId, String dictCode, UpdateDictTypeRequest request, String actor) {
    DictTypeRecord existing =
        findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));

    if (existing.systemBuiltin() && request.enabled() != null && !request.enabled()) {
      throw new IllegalArgumentException(
          "system builtin dict type '" + dictCode + "' cannot be disabled");
    }

    // 内置字典只能更新元数据（dict_name, description, sort_order），不能改 enabled
    Boolean nextEnabled = existing.systemBuiltin() ? null : request.enabled();

    int rows =
        jdbc.update(
            """
                update platform_dict_type
                   set dict_name   = coalesce(?, dict_name),
                       description = coalesce(?, description),
                       enabled     = coalesce(?, enabled),
                       sort_order  = coalesce(?, sort_order),
                       updated_at  = now()
                 where tenant_id = ? and dict_code = ?
                """,
            request.dictName(),
            request.description(),
            nextEnabled,
            request.sortOrder(),
            tenantId,
            dictCode);
    if (rows == 0) {
      return Optional.empty();
    }
    return findType(tenantId, dictCode);
  }

  public Optional<DictTypeRecord> disableType(String tenantId, String dictCode) {
    DictTypeRecord existing =
        findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));
    if (existing.systemBuiltin()) {
      throw new IllegalArgumentException("system builtin dict type cannot be disabled");
    }

    int rows =
        jdbc.update(
            """
                update platform_dict_type
                   set enabled = false,
                       updated_at = now()
                 where tenant_id = ? and dict_code = ?
                """,
            tenantId,
            dictCode);

    if (rows == 0) {
      return Optional.empty();
    }
    return findType(tenantId, dictCode);
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode) {
    return listItems(tenantId, dictCode, false);
  }

  public List<DictItemRecord> listItems(String tenantId, String dictCode, boolean includeDisabled) {
    return jdbc.query(
        """
            select i.id, i.tenant_id, i.dict_type_id, i.item_label, i.item_value, i.color,
                   i.icon, i.description, i.system_builtin, i.enabled, i.sort_order,
                   i.extra_json::text, i.created_by, i.created_at, i.updated_at
            from platform_dict_item i
            join platform_dict_type t on t.id = i.dict_type_id and t.tenant_id = i.tenant_id
            where i.tenant_id = ? and t.dict_code = ?
              and (? = true or i.enabled = true)
            order by i.sort_order asc, i.created_at asc
            """,
        (rs, rowNum) -> mapItem(rs),
        tenantId,
        dictCode,
        includeDisabled);
  }

  public DictItemRecord createItem(
      String tenantId, String dictCode, CreateDictItemRequest request, String createdBy) {
    DictTypeRecord type =
        findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));
    String id = Ids.newId();
    jdbc.update(
        """
            insert into platform_dict_item(
              id, tenant_id, dict_type_id, item_label, item_value, color, icon, description,
              enabled, sort_order, extra_json, created_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """,
        id,
        tenantId,
        type.id(),
        request.itemLabel(),
        request.itemValue(),
        request.color(),
        request.icon(),
        request.description(),
        request.enabled() == null || request.enabled(),
        request.sortOrder() == null ? 0 : request.sortOrder(),
        blankJson(request.extraJson()),
        createdBy);
    return listItems(tenantId, dictCode, true).stream()
        .filter(item -> item.id().equals(id))
        .findFirst()
        .orElseThrow();
  }

  public Optional<DictItemRecord> findItem(String tenantId, String dictCode, String itemId) {
    return listItems(tenantId, dictCode, true).stream()
        .filter(item -> item.id().equals(itemId))
        .findFirst();
  }

  public Optional<DictItemRecord> updateItem(
      String tenantId, String dictCode, String itemId, UpdateDictItemRequest request) {
    DictTypeRecord type =
        findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));
    int rows =
        jdbc.update(
            """
                update platform_dict_item
                   set item_label   = coalesce(?, item_label),
                       color        = coalesce(?, color),
                       icon         = coalesce(?, icon),
                       description  = coalesce(?, description),
                       enabled      = coalesce(?, enabled),
                       sort_order   = coalesce(?, sort_order),
                       updated_at   = now()
                 where tenant_id = ? and dict_type_id = ? and id = ?
                """,
            request.itemLabel(),
            request.color(),
            request.icon(),
            request.description(),
            request.enabled(),
            request.sortOrder(),
            tenantId,
            type.id(),
            itemId);
    if (rows == 0) {
      return Optional.empty();
    }
    return listItems(tenantId, dictCode, true).stream()
        .filter(item -> item.id().equals(itemId))
        .findFirst();
  }

  public Optional<DictItemRecord> disableItem(String tenantId, String dictCode, String itemId) {
    DictTypeRecord type =
        findType(tenantId, dictCode)
            .orElseThrow(() -> new IllegalArgumentException("dict type not found"));

    int rows =
        jdbc.update(
            """
                update platform_dict_item
                   set enabled = false,
                       updated_at = now()
                 where tenant_id = ? and dict_type_id = ? and id = ?
                """,
            tenantId,
            type.id(),
            itemId);

    if (rows == 0) {
      return Optional.empty();
    }

    return listItems(tenantId, dictCode, true).stream()
        .filter(item -> item.id().equals(itemId))
        .findFirst();
  }

  private DictTypeRecord mapType(ResultSet rs) throws SQLException {
    return new DictTypeRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("dict_code"),
        rs.getString("dict_name"),
        rs.getString("description"),
        rs.getBoolean("system_builtin"),
        rs.getBoolean("enabled"),
        rs.getInt("sort_order"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private DictItemRecord mapItem(ResultSet rs) throws SQLException {
    return new DictItemRecord(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("dict_type_id"),
        rs.getString("item_label"),
        rs.getString("item_value"),
        rs.getString("color"),
        rs.getString("icon"),
        rs.getString("description"),
        rs.getBoolean("system_builtin"),
        rs.getBoolean("enabled"),
        rs.getInt("sort_order"),
        rs.getString("extra_json"),
        rs.getString("created_by"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class));
  }

  private String blankJson(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }
}
