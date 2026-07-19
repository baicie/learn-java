package io.aegisops.ai.model.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.aegisops.ai.model.api.CreateAiModelRequest;
import io.aegisops.ai.model.domain.AiModelConfig;
import io.aegisops.ai.model.domain.DeepSeekConnectionResult;
import io.aegisops.ai.model.port.AiModelAudit;
import io.aegisops.ai.model.port.AiModelStore;
import io.aegisops.ai.model.port.DeepSeekConnectionClient;
import io.aegisops.ai.model.port.SecretCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiModelServiceTest {
  private final AiModelStore store = mock(AiModelStore.class);
  private final SecretCipher cipher = mock(SecretCipher.class);
  private final DeepSeekConnectionClient connection = mock(DeepSeekConnectionClient.class);
  private final AiModelAudit audit = mock(AiModelAudit.class);
  private final AiModelService service = new AiModelService(store, cipher, connection, audit);

  @Test
  void createEncryptsApiKeyAndNeverReturnsIt() {
    when(cipher.encrypt("sk-secret")).thenReturn("encrypted-value");
    when(store.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        service.create(
            "tenant-1",
            "admin",
            new CreateAiModelRequest(
                "deepseek",
                "生产模型",
                "deepseek-chat",
                "https://api.deepseek.com/v1/",
                "sk-secret",
                true));

    assertThat(result.provider()).isEqualTo("deepseek");
    assertThat(result.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
    assertThat(result.apiKeyConfigured()).isTrue();
    assertThat(result.toString()).doesNotContain("sk-secret").doesNotContain("encrypted-value");
    verify(audit).record("tenant-1", "admin", "ai_model.create", result.id(), result);
  }

  @Test
  void setDefaultRejectsDisabledModel() {
    when(store.find("tenant-1", "model-1"))
        .thenReturn(Optional.of(model(false, false, "encrypted-value")));

    assertThatThrownBy(() -> service.setDefault("tenant-1", "admin", "model-1"))
        .hasMessageContaining("启用");
  }

  @Test
  void testConnectionDecryptsOnlyInsideConnectionCall() {
    when(store.find("tenant-1", "model-1"))
        .thenReturn(Optional.of(model(true, false, "encrypted-value")));
    when(cipher.decrypt("encrypted-value")).thenReturn("sk-secret");
    when(connection.test("https://api.deepseek.com/v1", "sk-secret", "deepseek-chat"))
        .thenReturn(new DeepSeekConnectionResult(true, "连接成功"));
    when(store.updateTestResult(any(), any(), any(), any()))
        .thenAnswer(invocation -> model(true, false, "encrypted-value"));

    var result = service.testConnection("tenant-1", "admin", "model-1");

    assertThat(result.success()).isTrue();
    assertThat(result.message()).isEqualTo("连接成功");
  }

  private AiModelConfig model(boolean enabled, boolean defaultModel, String encryptedKey) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return new AiModelConfig(
        "model-1",
        "tenant-1",
        "deepseek",
        "生产模型",
        "deepseek-chat",
        "https://api.deepseek.com/v1",
        encryptedKey,
        enabled,
        defaultModel,
        null,
        null,
        null,
        now,
        now);
  }
}
