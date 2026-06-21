package io.aegisops.datasource.zabbix;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZabbixTagNormalizerTest {
  record DemoTag(String tag, String value) {}

  @Test
  void shouldNormalizePlainMap() {
    Map<String, String> tags =
        ZabbixTagNormalizer.normalize(
            Map.of(
                "app", "mall",
                "env", "prod",
                "service", "order-service"));

    assertThat(tags)
        .containsEntry("app", "mall")
        .containsEntry("env", "prod")
        .containsEntry("service", "order-service");
  }

  @Test
  void shouldNormalizeZabbixTagList() {
    Map<String, String> tags =
        ZabbixTagNormalizer.normalize(
            List.of(
                Map.of("tag", "app", "value", "mall"),
                Map.of("tag", "env", "value", "prod"),
                Map.of("tag", "service", "value", "order-service")));

    assertThat(tags)
        .containsEntry("app", "mall")
        .containsEntry("env", "prod")
        .containsEntry("service", "order-service");
  }

  @Test
  void shouldNormalizeRecordTagsByReflection() {
    Map<String, String> tags =
        ZabbixTagNormalizer.normalize(
            List.of(new DemoTag("app", "mall"), new DemoTag("service", "order-service")));

    assertThat(tags).containsEntry("app", "mall").containsEntry("service", "order-service");
  }

  @Test
  void shouldNormalizeDashKey() {
    Map<String, String> tags =
        ZabbixTagNormalizer.normalize(Map.of("service-name", "order-service"));

    assertThat(tags).containsEntry("service_name", "order-service");
  }

  @Test
  void shouldReturnFirstMatchedValue() {
    Map<String, String> tags =
        ZabbixTagNormalizer.normalize(
            Map.of(
                "application", "mall",
                "component", "order-service"));

    assertThat(ZabbixTagNormalizer.first(tags, "app", "application")).isEqualTo("mall");
    assertThat(ZabbixTagNormalizer.first(tags, "service", "component")).isEqualTo("order-service");
  }
}
