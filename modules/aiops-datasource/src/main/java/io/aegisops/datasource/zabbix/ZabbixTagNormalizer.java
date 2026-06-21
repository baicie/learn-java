package io.aegisops.datasource.zabbix;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ZabbixTagNormalizer {
  private ZabbixTagNormalizer() {}

  public static Map<String, String> normalize(Object zabbixTags) {
    Map<String, String> result = new LinkedHashMap<>();

    if (zabbixTags == null) {
      return result;
    }

    if (zabbixTags instanceof Map<?, ?> map) {
      normalizeMapTags(map, result);
      return result;
    }

    if (zabbixTags instanceof Iterable<?> iterable) {
      for (Object item : iterable) {
        normalizeOneTagItem(item, result);
      }
      return result;
    }

    normalizeOneTagItem(zabbixTags, result);
    return result;
  }

  public static String first(Map<String, String> tags, String... keys) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }

    for (String key : keys) {
      if (key == null) {
        continue;
      }

      String value = tags.get(normalizeKey(key));
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }

    return null;
  }

  private static void normalizeMapTags(Map<?, ?> source, Map<String, String> result) {
    if (source.containsKey("tag") || source.containsKey("name") || source.containsKey("key")) {
      String key = valueOf(firstValue(source, "tag", "name", "key"));
      String value = valueOf(firstValue(source, "value", "val"));
      putIfValid(result, key, value);
      return;
    }

    for (Map.Entry<?, ?> entry : source.entrySet()) {
      putIfValid(result, valueOf(entry.getKey()), valueOf(entry.getValue()));
    }
  }

  private static void normalizeOneTagItem(Object item, Map<String, String> result) {
    if (item == null) {
      return;
    }

    if (item instanceof Map<?, ?> map) {
      normalizeMapTags(map, result);
      return;
    }

    String key = valueOf(invokeZeroArg(item, "tag"));
    if (key == null) {
      key = valueOf(invokeZeroArg(item, "name"));
    }
    if (key == null) {
      key = valueOf(invokeZeroArg(item, "key"));
    }

    String value = valueOf(invokeZeroArg(item, "value"));
    if (value == null) {
      value = valueOf(invokeZeroArg(item, "val"));
    }

    putIfValid(result, key, value);
  }

  private static Object firstValue(Map<?, ?> source, String... keys) {
    for (String key : keys) {
      if (source.containsKey(key)) {
        return source.get(key);
      }
    }
    return null;
  }

  private static Object invokeZeroArg(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (ReflectiveOperationException ex) {
      return null;
    }
  }

  private static void putIfValid(Map<String, String> result, String key, String value) {
    if (key == null || key.isBlank() || value == null || value.isBlank()) {
      return;
    }

    result.put(normalizeKey(key), value.trim());
  }

  private static String normalizeKey(String key) {
    return key.trim().toLowerCase(Locale.ROOT).replace('-', '_');
  }

  private static String valueOf(Object value) {
    if (value == null) {
      return null;
    }

    String text = String.valueOf(value);
    return text.isBlank() ? null : text.trim();
  }
}
