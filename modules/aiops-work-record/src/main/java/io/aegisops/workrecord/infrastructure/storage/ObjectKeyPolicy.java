package io.aegisops.workrecord.infrastructure.storage;

final class ObjectKeyPolicy {
  private ObjectKeyPolicy() {}

  static String requireValid(String objectKey) {
    if (objectKey == null
        || objectKey.isBlank()
        || objectKey.startsWith("/")
        || objectKey.contains("..")
        || objectKey.length() > 512) {
      throw new IllegalArgumentException("invalid object key");
    }
    return objectKey;
  }
}
