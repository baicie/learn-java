package io.aegisops.workrecord.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.aegisops.workrecord.application.port.ObjectStoragePort;
import io.aegisops.workrecord.application.port.ObjectStorageUrlSigner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ObjectStorageFeatureToggleTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("aiops.runtime.app=worker")
          .withUserConfiguration(ObjectStorageTestConfiguration.class);

  @Test
  void usesFailClosedStorageWithoutCreatingMinioClientsByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(ObjectStoragePort.class);
          assertThat(context).hasSingleBean(ObjectStorageUrlSigner.class);
          assertThat(context.getBean(ObjectStoragePort.class))
              .isInstanceOf(DisabledObjectStorage.class);
          assertThat(context.getBean(ObjectStorageUrlSigner.class))
              .isInstanceOf(DisabledObjectStorage.class);
          assertThat(context).doesNotHaveBean(MinioObjectStorageAdapter.class);
          assertThat(context).doesNotHaveBean(MinioObjectStorageUrlSigner.class);
        });
  }

  @Test
  void createsMinioClientsOnlyWhenObjectStorageIsEnabled() {
    contextRunner
        .withPropertyValues(
            "aiops.object-storage.enabled=true",
            "aiops.object-storage.endpoint=http://minio:9000",
            "aiops.object-storage.access-key=test-access",
            "aiops.object-storage.secret-key=test-secret",
            "aiops.object-storage.bucket=test-bucket")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObjectStoragePort.class);
              assertThat(context).hasSingleBean(ObjectStorageUrlSigner.class);
              assertThat(context.getBean(ObjectStoragePort.class))
                  .isInstanceOf(MinioObjectStorageAdapter.class);
              assertThat(context.getBean(ObjectStorageUrlSigner.class))
                  .isInstanceOf(MinioObjectStorageUrlSigner.class);
              assertThat(context).doesNotHaveBean(DisabledObjectStorage.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({
    DisabledObjectStorage.class,
    MinioObjectStorageAdapter.class,
    MinioObjectStorageUrlSigner.class,
    ObjectStorageConfiguration.class
  })
  static class ObjectStorageTestConfiguration {}
}
