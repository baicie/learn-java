package io.aegisops.integration.zabbix;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.alert.AlertIngestRequest;
import io.aegisops.alert.AlertIngestResult;
import io.aegisops.alert.AlertIngestService;
import io.aegisops.asset.application.AssetQueryService;
import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ZabbixWebhookService {
  private final JdbcTemplate jdbc;
  private final AssetQueryService assetQueryService;
  private final AlertIngestService alertIngestService;
  private final ObjectMapper objectMapper;
  private final ZabbixWebhookTokenVerifier tokenVerifier;
  private final ZabbixWebhookMapper mapper;

  public ZabbixWebhookService(
      JdbcTemplate jdbc,
      AssetQueryService assetQueryService,
      AlertIngestService alertIngestService,
      ObjectMapper objectMapper,
      ZabbixWebhookTokenVerifier tokenVerifier,
      ZabbixWebhookMapper mapper) {
    this.jdbc = jdbc;
    this.assetQueryService = assetQueryService;
    this.alertIngestService = alertIngestService;
    this.objectMapper = objectMapper;
    this.tokenVerifier = tokenVerifier;
    this.mapper = mapper;
  }

  @Transactional
  public ZabbixWebhookIngestResponse ingest(
      String datasourceId, String token, ZabbixWebhookPayload payload) {
    ZabbixWebhookAlertMapping mapping = mapper.map(datasourceId, payload);
    if (!tokenVerifier.verify(mapping.datasourceId(), token)) {
      throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid Zabbix webhook token");
    }

    DataSourceBinding datasource = getDatasourceBinding(mapping.datasourceId());
    String assetId = resolveAssetId(datasource.tenantId(), mapping);

    return ingestAlert(datasource, mapping, assetId);
  }

  private ZabbixWebhookIngestResponse ingestAlert(
      DataSourceBinding datasource, ZabbixWebhookAlertMapping mapping, String assetId) {
    AlertIngestResult result =
        alertIngestService.ingest(
            datasource.tenantId(),
            new AlertIngestRequest(
                "zabbix",
                mapping.sourceEventId(),
                mapping.severity(),
                mapping.title(),
                mapping.description(),
                assetId,
                mapping.entityType(),
                mapping.entityName(),
                mapping.labels(),
                mapping.startsAt(),
                mapping.endsAt(),
                mapping.status(),
                rawPayload(mapping.rawPayload())),
            mapping.fingerprint(),
            mapping.aggregationKey());

    ZabbixWebhookIngestResponse response =
        new ZabbixWebhookIngestResponse(
            result.alertId(),
            datasource.id(),
            datasource.tenantId(),
            mapping.sourceEventId(),
            result.status(),
            result.created(),
            result.created() ? "Zabbix alert event created" : "Zabbix alert event updated");
    return response;
  }

  private DataSourceBinding getDatasourceBinding(String datasourceId) {
    try {
      return jdbc.queryForObject(
          """
          select d.id, d.tenant_id
          from datasource d
          join tenant t on t.id = d.tenant_id and t.status = 'active'
          where d.id = ? and d.type = 'zabbix' and d.status = 'active'
          """,
          (rs, rowNum) -> new DataSourceBinding(rs.getString("id"), rs.getString("tenant_id")),
          datasourceId);
    } catch (EmptyResultDataAccessException ex) {
      throw new AppException("ZABBIX_DATASOURCE_NOT_FOUND", "Zabbix datasource not found");
    }
  }

  private String resolveAssetId(String tenantId, ZabbixWebhookAlertMapping mapping) {
    if (mapping.hostIds().isEmpty()) {
      return null;
    }
    return assetQueryService
        .findAssetIdBySourceLink(
            tenantId, "zabbix", mapping.datasourceId(), mapping.hostIds().getFirst())
        .orElse(null);
  }

  private Map<String, Object> rawPayload(Object value) {
    if (value == null) {
      return Map.of();
    }
    return objectMapper.convertValue(value, new TypeReference<>() {});
  }

  private record DataSourceBinding(String id, String tenantId) {}
}
