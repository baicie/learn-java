package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordQueryPolicy {

  private final WorkRecordProductionProperties properties;

  public WorkRecordQueryPolicy(WorkRecordProductionProperties properties) {
    this.properties = properties;
  }

  public PageWindow normalize(int requestedPage, int requestedSize) {
    int page = Math.max(1, requestedPage);

    int size =
        Math.min(
            Math.max(1, requestedSize), properties.getQuery().getMaxPageSize());

    long offset;

    try {
      offset = Math.multiplyExact((long) page - 1L, (long) size);
    } catch (ArithmeticException ex) {
      throw new AppException(
          ErrorCode.PAGE_WINDOW_EXCEEDED, "page window is too large", ex);
    }

    long maxOffset = properties.getQuery().getMaxOffset();

    if (offset > maxOffset) {
      throw new AppException(
          ErrorCode.PAGE_WINDOW_EXCEEDED,
          "page offset exceeds " + maxOffset + "; narrow filters or use export");
    }

    return new PageWindow(page, size, offset);
  }

  public record PageWindow(int page, int size, long offset) {}
}