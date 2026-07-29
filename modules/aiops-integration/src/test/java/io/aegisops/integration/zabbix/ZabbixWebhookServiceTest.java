package io.aegisops.integration.zabbix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.alert.AlertIngestResult;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.asset.application.AssetQueryService;
import io.aegisops.common.exception.AppException;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ZabbixWebhookServiceTest {

  @Test
  void shouldRejectInvalidTokenBeforeDatabaseAccess() {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    AssetQueryService assetQueryService = org.mockito.Mockito.mock(AssetQueryService.class);
    AlertIngestService alertIngestService = org.mockito.Mockito.mock(AlertIngestService.class);
    ZabbixWebhookService service =
        new ZabbixWebhookService(
            jdbc,
            assetQueryService,
            alertIngestService,
            objectMapper(),
            new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret")),
            new ZabbixWebhookMapper());

    ZabbixWebhookPayload payload =
        new ZabbixWebhookPayload(
            "ds_1",
            "20001",
            null,
            null,
            "30001",
            null,
            "1",
            "PROBLEM",
            "High",
            "CPU High",
            "CPU high",
            "10084",
            "host",
            null,
            "mall",
            "demo",
            null,
            "order-service",
            null,
            null,
            null,
            OffsetDateTime.parse("2026-06-21T05:10:00Z"),
            null,
            null,
            Map.of());

    assertThatThrownBy(() -> service.ingest("ds_1", "bad", payload))
        .isInstanceOfSatisfying(
            AppException.class,
            exception -> {
              assertThat(exception.errorCode()).isEqualTo("UNAUTHORIZED");
              assertThat(exception.httpStatus()).isEqualTo(401);
            })
        .hasMessageContaining("Invalid Zabbix webhook token");

    verifyNoInteractions(jdbc, assetQueryService, alertIngestService);
  }

  @Test
  void shouldIngestThroughAlertFacadeWithDatasourceScopedIdentity() throws Exception {
    JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
    AssetQueryService assetQueryService = org.mockito.Mockito.mock(AssetQueryService.class);
    AlertIngestService alertIngestService = org.mockito.Mockito.mock(AlertIngestService.class);
    ZabbixWebhookTokenVerifier verifier =
        new ZabbixWebhookTokenVerifier(new ZabbixWebhookProperties("secret"));
    stubActiveDatasourceBinding(jdbc);
    stubAlertIngest(assetQueryService, alertIngestService);
    ZabbixWebhookService service =
        new ZabbixWebhookService(
            jdbc,
            assetQueryService,
            alertIngestService,
            objectMapper(),
            verifier,
            new ZabbixWebhookMapper());

    ZabbixWebhookIngestResponse response =
        service.ingest("ds_1", verifier.tokenForDatasource("ds_1"), problemPayload());

    ArgumentCaptor<String> bindingSql = ArgumentCaptor.forClass(String.class);
    verify(jdbc).queryForObject(bindingSql.capture(), any(RowMapper.class), eq("ds_1"));
    assertThat(bindingSql.getValue())
        .contains("join tenant t on t.id = d.tenant_id")
        .contains("t.status = 'active'");
    ArgumentCaptor<AlertIngestRequest> request = ArgumentCaptor.forClass(AlertIngestRequest.class);
    verify(alertIngestService)
        .ingest(
            eq("tenant_1"),
            request.capture(),
            eq("zabbix:ds_1:30001"),
            eq("zabbix:ds_1:10084:svc:demo:202606210510"));
    assertThat(request.getValue().assetId()).isEqualTo("asset_1");
    assertThat(request.getValue().rawPayload()).containsEntry("eventId", "20001");
    assertThat(response.alertId()).isEqualTo("alert_1");
    assertThat(response.tenantId()).isEqualTo("tenant_1");
    assertThat(response.status()).isEqualTo("resolved");
  }

  private void stubActiveDatasourceBinding(JdbcTemplate jdbc) throws Exception {
    when(jdbc.queryForObject(anyString(), any(RowMapper.class), any(Object[].class)))
        .thenAnswer(
            invocation -> {
              ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
              when(resultSet.getString("id")).thenReturn("ds_1");
              when(resultSet.getString("tenant_id")).thenReturn("tenant_1");
              RowMapper<?> rowMapper = invocation.getArgument(1);
              return rowMapper.mapRow(resultSet, 0);
            });
  }

  private void stubAlertIngest(
      AssetQueryService assetQueryService, AlertIngestService alertIngestService) {
    when(assetQueryService.findAssetIdBySourceLink("tenant_1", "zabbix", "ds_1", "10084"))
        .thenReturn(Optional.of("asset_1"));
    when(alertIngestService.ingest(
            eq("tenant_1"), any(AlertIngestRequest.class), anyString(), anyString()))
        .thenReturn(
            new AlertIngestResult(
                "alert_1",
                true,
                "resolved",
                "zabbix:ds_1:30001",
                "zabbix:ds_1:10084:svc:demo:bucket"));
  }

  private ZabbixWebhookPayload problemPayload() {
    return new ZabbixWebhookPayload(
        "ds_1",
        "20001",
        null,
        null,
        "30001",
        null,
        "1",
        "PROBLEM",
        "High",
        "CPU High",
        "CPU high",
        "10084",
        "host",
        null,
        "app",
        "demo",
        null,
        "svc",
        null,
        null,
        null,
        OffsetDateTime.parse("2026-06-21T05:10:00Z"),
        null,
        null,
        Map.of());
  }

  private ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
