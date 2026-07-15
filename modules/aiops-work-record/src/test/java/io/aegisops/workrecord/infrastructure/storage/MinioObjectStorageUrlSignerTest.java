package io.aegisops.workrecord.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MinioObjectStorageUrlSignerTest {
  private final MinioObjectStorageUrlSigner signer =
      new MinioObjectStorageUrlSigner(
          new ObjectStorageProperties(null, null, null, null, 0, 0, null));

  @Test
  void signsDirectUploadWithoutContactingObjectStorage() {
    String url =
        signer.presignedPut(
            "tenant-1/imports/upload-1/source.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            Duration.ofMinutes(5));

    assertThat(url)
        .startsWith("http://localhost:9002/")
        .contains("X-Amz-Algorithm")
        .contains("X-Amz-Signature");
  }

  @Test
  void rejectsUnsafeKeysAndExcessiveExpiry() {
    assertThatThrownBy(() -> signer.presignedPut("../secret", "text/plain", Duration.ofMinutes(5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> signer.presignedPut("tenant-1/source.xlsx", "text/plain", Duration.ofDays(8)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
