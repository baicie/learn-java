package io.aegisops.asset.infrastructure.adapter;

import java.util.Map;

public record AssetCsvRow(
    int rowNumber,
    String externalId,
    String assetType,
    String name,
    String displayName,
    String environment,
    String site,
    String ownerTeam,
    String criticality,
    String ip,
    String machineId,
    String cloudInstanceId,
    String k8sUid,
    Map<String, String> tags) {}
