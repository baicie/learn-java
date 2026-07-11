package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkRecordPayloadPolicyTest {

  private WorkRecordProductionProperties properties;
  private WorkRecordPayloadPolicy policy;

  @BeforeEach
  void setUp() {
    properties = new WorkRecordProductionProperties();
    properties.getPayload().setCustomDataMaxBytes(10);
    policy = new WorkRecordPayloadPolicy(properties);
  }

  @Test
  void acceptsValueAtByteLimit() {
    assertThatCode(() -> policy.requireCustomData("1234567890")).doesNotThrowAnyException();
  }

  @Test
  void rejectsUtf8ValueByBytesNotCharacters() {
    assertThatThrownBy(() -> policy.requireCustomData("中文中文"))
        .isInstanceOf(AppException.class)
        .satisfies(
            throwable ->
                assertThat(((AppException) throwable).errorCode())
                    .isEqualTo(ErrorCode.CUSTOM_DATA_JSON_TOO_LARGE.name()));
  }
}
