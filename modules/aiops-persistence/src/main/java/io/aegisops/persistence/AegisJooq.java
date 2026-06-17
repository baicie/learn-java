package io.aegisops.persistence;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;

/**
 * jOOQ 写入/默认值辅助方法。
 *
 * <p>命名上特意避开 {@code jsonb}，避免与 {@link AegisTables#jsonb} 字段工厂同名造成静态 import 冲突。
 */
public final class AegisJooq {
  private AegisJooq() {}

  /** 把字符串以 {@code '...': :jsonb} 的形式渲染为 jsonb 对象，空串或 null 回退 {@code {}}。 */
  public static Field<JSONB> jsonbValue(String json) {
    String value = json == null || json.isBlank() ? "{}" : json;
    return DSL.field("{0}::jsonb", JSONB.class, DSL.val(value));
  }

  /** 把字符串以 {@code '...': :jsonb} 的形式渲染为 jsonb 数组，空串或 null 回退 {@code []}。 */
  public static Field<JSONB> jsonbArrayValue(String json) {
    String value = json == null || json.isBlank() ? "[]" : json;
    return DSL.field("{0}::jsonb", JSONB.class, DSL.val(value));
  }

  /** 把字符串规整为非空 JSON object：null/blank 视为 {@code {}}。 */
  public static String jsonObjectOrEmpty(String value) {
    return value == null || value.isBlank() ? "{}" : value;
  }

  /** 把字符串规整为非空 JSON array：null/blank 视为 {@code []}。 */
  public static String jsonArrayOrEmpty(String value) {
    return value == null || value.isBlank() ? "[]" : value;
  }
}
