package io.aegisops.workrecord.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.common.id.Ids;
import io.aegisops.security.PermissionCodes;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.command.CreateTemplateCommand;
import io.aegisops.workrecord.application.command.PublishTemplateCommand;
import io.aegisops.workrecord.application.port.TemplateMarketRepository;
import io.aegisops.workrecord.domain.model.WorkRecordTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateMarketService {
  private static final Set<String> VISIBILITIES = Set.of("private", "tenant", "public");
  private final TemplateMarketRepository market;
  private final WorkRecordTemplateService templates;
  private final WorkRecordTemplateVersionService versions;
  private final ObjectMapper objectMapper;

  public TemplateMarketService(
      TemplateMarketRepository market,
      WorkRecordTemplateService templates,
      WorkRecordTemplateVersionService versions,
      ObjectMapper objectMapper) {
    this.market = market;
    this.templates = templates;
    this.versions = versions;
    this.objectMapper = objectMapper;
  }

  public List<TemplateMarketRepository.MarketVersion> list(String tenantId) {
    return market.listVisible(tenantId);
  }

  @Transactional
  public TemplateMarketRepository.MarketVersion publish(
      String tenantId, PublishPackage command, UserPrincipal principal) {
    require(tenantId, principal, PermissionCodes.WORK_RECORD_MARKET_PUBLISH);
    if (command == null) throw new IllegalArgumentException("market package is required");
    requireText(command.templateId(), "templateId");
    requireText(command.packageCode(), "packageCode");
    requireText(command.name(), "name");
    if (!VISIBILITIES.contains(command.visibility())) {
      throw new IllegalArgumentException("unsupported market visibility");
    }
    WorkRecordTemplate template = templates.get(tenantId, command.templateId());
    if (template.currentVersionId() == null) {
      throw new IllegalStateException("only a published template can enter the market");
    }
    var version = versions.get(tenantId, template.id(), template.currentVersionId());
    PackageDocument document =
        new PackageDocument(
            1,
            command.packageCode(),
            template.code(),
            template.name(),
            template.description(),
            version.schemaJson(),
            version.designerJson());
    String json = write(document);
    return market.publish(
        new TemplateMarketRepository.PublishPackage(
            tenantId,
            template.id(),
            command.packageCode(),
            command.name(),
            command.category(),
            command.visibility(),
            json,
            sha256(json),
            principal.id()));
  }

  @Transactional
  public WorkRecordTemplate install(
      String tenantId, String versionId, String targetCode, UserPrincipal principal) {
    require(tenantId, principal, PermissionCodes.WORK_RECORD_MARKET_INSTALL);
    requireText(versionId, "versionId");
    requireText(targetCode, "targetTemplateCode");
    var version = market.requireVisible(tenantId, versionId);
    if (!sha256(version.packageJson()).equals(version.checksum())) {
      throw new IllegalStateException("market package checksum mismatch");
    }
    PackageDocument document = read(version.packageJson());
    if (document.contractVersion() != 1) {
      throw new IllegalStateException("unsupported market package contract");
    }
    WorkRecordTemplate template =
        templates.create(
            tenantId,
            new CreateTemplateCommand(
                targetCode,
                document.templateName(),
                document.description(),
                document.schemaJson(),
                document.designerJson()),
            principal.id());
    versions.publish(
        tenantId, new PublishTemplateCommand(template.id(), "market-v1"), principal.id());
    market.recordInstall(
        Ids.newId(), tenantId, version.packageId(), version.id(), template.id(), principal.id());
    return templates.get(tenantId, template.id());
  }

  private void require(String tenantId, UserPrincipal principal, String permission) {
    if (principal == null
        || !tenantId.equals(principal.tenantId())
        || !principal.hasPermission(permission)) {
      throw new AccessDeniedException("template market permission denied");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("cannot serialize market package", ex);
    }
  }

  private PackageDocument read(String value) {
    try {
      return objectMapper.readValue(value, PackageDocument.class);
    } catch (Exception ex) {
      throw new IllegalStateException("invalid market package", ex);
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  public record PublishPackage(
      String templateId, String packageCode, String name, String category, String visibility) {}

  public record PackageDocument(
      int contractVersion,
      String packageCode,
      String templateCode,
      String templateName,
      String description,
      String schemaJson,
      String designerJson) {}
}
