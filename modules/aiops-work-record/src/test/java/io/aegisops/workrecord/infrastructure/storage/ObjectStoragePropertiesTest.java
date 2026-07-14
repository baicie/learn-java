package io.aegisops.workrecord.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class ObjectStoragePropertiesTest {

  @Test
  void defaultsMatchLocalMinioTopology() {
    ObjectStorageProperties properties =
        new ObjectStorageProperties(null, null, null, null, 0, 0, null);

    assertThat(properties.endpoint()).isEqualTo("http://localhost:9002");
    assertThat(properties.bucket()).isEqualTo("aegisops-work-record");
    assertThat(properties.attachmentMaxBytes()).isEqualTo(20L * 1024L * 1024L);
    assertThat(properties.downloadUrlExpirySeconds()).isEqualTo(300);
  }

  @Test
  void adapterRejectsUnsafeObjectKeyBeforeNetworkAccess() {
    MinioObjectStorageAdapter adapter =
        new MinioObjectStorageAdapter(
            new ObjectStorageProperties(null, null, null, null, 0, 0, null));

    assertThatThrownBy(
            () ->
                adapter.putUnknownLength(
                    "tenant-1/../tenant-2/secret",
                    "text/plain",
                    new ByteArrayInputStream(new byte[0]),
                    1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("object key");
  }
}
