package io.aegisops.ai.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.ai.model.api.AiModelController;
import io.aegisops.ai.model.application.AiModelService;
import io.aegisops.ai.model.infrastructure.AesGcmSecretCipher;
import io.aegisops.ai.model.infrastructure.DefaultAiModelAudit;
import io.aegisops.ai.model.infrastructure.HttpDeepSeekConnectionClient;
import io.aegisops.ai.model.infrastructure.JdbcAiModelStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiModelManagementRuntimeBoundaryTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              AiModelController.class,
              AiModelService.class,
              AesGcmSecretCipher.class,
              DefaultAiModelAudit.class,
              HttpDeepSeekConnectionClient.class,
              JdbcAiModelStore.class);

  @Test
  void workerDoesNotLoadServerSideAiModelManagement() {
    contextRunner
        .withPropertyValues("aiops.runtime.app=worker")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(AiModelController.class);
              assertThat(context).doesNotHaveBean(AiModelService.class);
              assertThat(context).doesNotHaveBean(AesGcmSecretCipher.class);
            });
  }
}
