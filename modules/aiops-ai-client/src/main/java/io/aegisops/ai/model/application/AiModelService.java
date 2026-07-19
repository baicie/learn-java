package io.aegisops.ai.model.application;

import io.aegisops.ai.model.api.AiModelResponse;
import io.aegisops.ai.model.api.CreateAiModelRequest;
import io.aegisops.ai.model.api.TestAiModelResponse;
import io.aegisops.ai.model.api.UpdateAiModelRequest;
import io.aegisops.ai.model.domain.AiModelConfig;
import io.aegisops.ai.model.domain.DeepSeekConnectionResult;
import io.aegisops.ai.model.port.AiModelAudit;
import io.aegisops.ai.model.port.AiModelStore;
import io.aegisops.ai.model.port.DeepSeekConnectionClient;
import io.aegisops.ai.model.port.SecretCipher;
import io.aegisops.common.exception.AppException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelService {
  private final AiModelStore store;
  private final SecretCipher cipher;
  private final DeepSeekConnectionClient connectionClient;
  private final AiModelAudit audit;

  public AiModelService(
      AiModelStore store,
      SecretCipher cipher,
      DeepSeekConnectionClient connectionClient,
      AiModelAudit audit) {
    this.store = store;
    this.cipher = cipher;
    this.connectionClient = connectionClient;
    this.audit = audit;
  }

  public List<AiModelResponse> list(String tenantId) {
    return store.list(tenantId).stream().map(AiModelResponse::from).toList();
  }

  @Transactional
  public AiModelResponse create(String tenantId, String actorId, CreateAiModelRequest request) {
    validateProvider(request.provider());
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    AiModelConfig created =
        store.insert(
            new AiModelConfig(
                newId(),
                tenantId,
                "deepseek",
                request.name().trim(),
                request.modelName().trim(),
                normalizeBaseUrl(request.baseUrl()),
                cipher.encrypt(request.apiKey().trim()),
                request.enabled(),
                false,
                null,
                null,
                null,
                now,
                now));
    AiModelResponse response = AiModelResponse.from(created);
    audit.record(tenantId, actorId, "ai_model.create", created.id(), response);
    return response;
  }

  @Transactional
  public AiModelResponse update(
      String tenantId, String actorId, String id, UpdateAiModelRequest request) {
    validateProvider(request.provider());
    AiModelConfig existing = requireModel(tenantId, id);
    String encryptedKey =
        request.apiKey() == null || request.apiKey().isBlank()
            ? existing.encryptedApiKey()
            : cipher.encrypt(request.apiKey().trim());
    AiModelConfig updated =
        store.update(
            new AiModelConfig(
                existing.id(),
                existing.tenantId(),
                "deepseek",
                request.name().trim(),
                request.modelName().trim(),
                normalizeBaseUrl(request.baseUrl()),
                encryptedKey,
                request.enabled(),
                request.enabled() && existing.defaultModel(),
                existing.lastTestStatus(),
                existing.lastTestMessage(),
                existing.lastTestedAt(),
                existing.createdAt(),
                OffsetDateTime.now(ZoneOffset.UTC)));
    AiModelResponse response = AiModelResponse.from(updated);
    audit.record(tenantId, actorId, "ai_model.update", id, response);
    return response;
  }

  @Transactional
  public void delete(String tenantId, String actorId, String id) {
    AiModelResponse before = AiModelResponse.from(requireModel(tenantId, id));
    store.delete(tenantId, id);
    audit.record(tenantId, actorId, "ai_model.delete", id, before);
  }

  @Transactional
  public AiModelResponse setDefault(String tenantId, String actorId, String id) {
    AiModelConfig existing = requireModel(tenantId, id);
    if (!existing.enabled()) {
      throw new AppException("AI_MODEL_DISABLED", "请先启用模型，再设为默认模型");
    }
    AiModelResponse response = AiModelResponse.from(store.setDefault(tenantId, id));
    audit.record(tenantId, actorId, "ai_model.set_default", id, response);
    return response;
  }

  @Transactional
  public TestAiModelResponse testConnection(String tenantId, String actorId, String id) {
    AiModelConfig model = requireModel(tenantId, id);
    DeepSeekConnectionResult result =
        connectionClient.test(
            model.baseUrl(), cipher.decrypt(model.encryptedApiKey()), model.modelName());
    String status = result.success() ? "success" : "failed";
    AiModelConfig tested = store.updateTestResult(tenantId, id, status, result.message());
    AiModelResponse response = AiModelResponse.from(tested);
    audit.record(tenantId, actorId, "ai_model.test", id, response);
    return new TestAiModelResponse(result.success(), result.message(), tested.lastTestedAt());
  }

  private AiModelConfig requireModel(String tenantId, String id) {
    return store
        .find(tenantId, id)
        .orElseThrow(() -> new AppException("AI_MODEL_NOT_FOUND", 404, "AI 模型不存在"));
  }

  private void validateProvider(String provider) {
    if (provider == null || !"deepseek".equals(provider.trim().toLowerCase(Locale.ROOT))) {
      throw new AppException("AI_PROVIDER_UNSUPPORTED", "当前仅支持 DeepSeek 供应商");
    }
  }

  private String normalizeBaseUrl(String value) {
    try {
      String normalized = value == null ? "" : value.trim().replaceAll("/+$", "");
      URI uri = URI.create(normalized);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || !"api.deepseek.com".equalsIgnoreCase(uri.getHost())) {
        throw new IllegalArgumentException();
      }
      return normalized;
    } catch (IllegalArgumentException exception) {
      throw new AppException(
          "AI_MODEL_BASE_URL_INVALID", "DeepSeek API 地址必须使用 https://api.deepseek.com");
    }
  }

  private String newId() {
    return "aim_" + UUID.randomUUID().toString().replace("-", "");
  }
}
