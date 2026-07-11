package io.aegisops.workrecord.application.port;

import java.time.Duration;

public interface WorkRecordTelemetry {

  void recordQuery(String operation, Duration duration);

  void recordExport(String result);

  void recordPermissionDenied(String action);

  static WorkRecordTelemetry noop() {
    return new WorkRecordTelemetry() {
      @Override
      public void recordQuery(String operation, Duration duration) {}

      @Override
      public void recordExport(String result) {}

      @Override
      public void recordPermissionDenied(String action) {}
    };
  }
}