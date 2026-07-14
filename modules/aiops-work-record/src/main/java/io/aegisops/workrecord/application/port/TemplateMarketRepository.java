package io.aegisops.workrecord.application.port;

import java.util.List;

public interface TemplateMarketRepository {
  MarketVersion publish(PublishPackage command);

  record PublishPackage(
      String tenantId,
      String sourceTemplateId,
      String packageCode,
      String name,
      String category,
      String visibility,
      String packageJson,
      String checksum,
      String actorId) {}

  MarketVersion requireVisible(String tenantId, String versionId);

  List<MarketVersion> listVisible(String tenantId);

  void recordInstall(
      String id,
      String tenantId,
      String packageId,
      String versionId,
      String templateId,
      String actorId);

  record MarketVersion(
      String id,
      String packageId,
      String packageCode,
      String name,
      String visibility,
      int versionNo,
      String packageJson,
      String checksum) {}
}
