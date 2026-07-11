package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkRecordQueryPolicyTest {

  private WorkRecordQueryPolicy policy;

  @BeforeEach
  void setUp() {
    WorkRecordProductionProperties properties = new WorkRecordProductionProperties();
    properties.getQuery().setMaxPageSize(200);
    properties.getQuery().setMaxOffset(100_000L);
    policy = new WorkRecordQueryPolicy(properties);
  }

  @Test
  void normalizesPageAndSize() {
    var window = policy.normalize(0, 1000);

    assertThat(window.page()).isEqualTo(1);
    assertThat(window.size()).isEqualTo(200);
    assertThat(window.offset()).isZero();
  }

  @Test
  void rejectsDeepOffset() {
    assertThatThrownBy(() -> policy.normalize(502, 200))
        .isInstanceOf(AppException.class)
        .satisfies(
            throwable ->
                assertThat(((AppException) throwable).errorCode())
                    .isEqualTo(ErrorCode.PAGE_WINDOW_EXCEEDED.name()));
  }

  @Test
  void multiplicationCannotOverflowInt() {
    assertThatThrownBy(() -> policy.normalize(Integer.MAX_VALUE, 200))
        .isInstanceOf(AppException.class);
  }
}
