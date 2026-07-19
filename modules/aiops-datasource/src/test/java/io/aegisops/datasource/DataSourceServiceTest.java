package io.aegisops.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.outbox.OutboxWriter;
import io.aegisops.kubernetes.application.KubernetesInventoryClientFactory;
import io.aegisops.zabbix.ZabbixClientFactory;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DataSourceServiceTest {
  @Test
  void updateKeepsExistingSecretWhenCredentialFieldsAreBlank() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    OffsetDateTime now = OffsetDateTime.parse("2026-07-18T00:00:00Z");
    DataSourceEntity entity =
        new DataSourceEntity(
            "ds-1",
            "tenant-1",
            "zabbix",
            "旧名称",
            "active",
            "{\"endpoint\":\"https://old.example\",\"apiToken\":\"secret-token\"}",
            now,
            now,
            null);
    DataSourceRecord updated =
        new DataSourceRecord(
            "ds-1", "tenant-1", "zabbix", "新名称", "https://new.example", "inactive", now, now, null);
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenReturn(entity, updated);
    DataSourceService service =
        new DataSourceService(
            jdbc,
            new ObjectMapper(),
            mock(ZabbixClientFactory.class),
            mock(KubernetesInventoryClientFactory.class),
            mock(OutboxWriter.class));

    DataSourceRecord result =
        service.update(
            "tenant-1",
            "ds-1",
            new UpdateDataSourceRequest(
                "新名称",
                new UpdateDataSourceRequest.ZabbixConfig(
                    "https://new.example", null, null, null, null, null),
                null,
                null));

    ArgumentCaptor<String> config = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .update(
            anyString(),
            config.capture(),
            org.mockito.ArgumentMatchers.eq("新名称"),
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq("ds-1"));
    assertThat(config.getValue()).contains("https://new.example").contains("secret-token");
    assertThat(result).isEqualTo(updated);
  }
}
