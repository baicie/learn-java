package io.aegisops.workrecord.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import io.aegisops.security.DataScope;
import io.aegisops.security.DistributedLease;
import io.aegisops.security.DistributedLeaseService;
import io.aegisops.security.RateLimitDecision;
import io.aegisops.security.RateLimitService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class WorkRecordExportGuardTest {

  @Test
  void rejectsRateLimitedUser() {
    RecordingTelemetry telemetry = new RecordingTelemetry();

    WorkRecordExportGuard guard =
        new WorkRecordExportGuard(
            (key, limit, window) -> RateLimitDecision.rejected(30),
            (key, ttl) -> Optional.empty(),
            new WorkRecordProductionProperties(),
            telemetry);

    assertThatThrownBy(() -> guard.acquire("tenant-1", user()))
        .isInstanceOf(AppException.class)
        .satisfies(
            throwable ->
                assertThat(((AppException) throwable).errorCode())
                    .isEqualTo(ErrorCode.EXPORT_RATE_LIMITED.name()));

    assertThat(telemetry.exportResult).isEqualTo("rate_limited");
  }

  @Test
  void rejectsConcurrentExport() {
    WorkRecordExportGuard guard =
        new WorkRecordExportGuard(
            allowedRateLimiter(),
            (key, ttl) -> Optional.empty(),
            new WorkRecordProductionProperties(),
            WorkRecordTelemetry.noop());

    assertThatThrownBy(() -> guard.acquire("tenant-1", user()))
        .isInstanceOf(AppException.class)
        .satisfies(
            throwable ->
                assertThat(((AppException) throwable).errorCode())
                    .isEqualTo(ErrorCode.EXPORT_IN_PROGRESS.name()));
  }

  @Test
  void closesLeaseAfterExport() {
    AtomicBoolean closed = new AtomicBoolean();

    DistributedLeaseService leases =
        (key, ttl) ->
            Optional.of(
                new DistributedLease() {
                  @Override
                  public String key() {
                    return key;
                  }

                  @Override
                  public void close() {
                    closed.set(true);
                  }
                });

    WorkRecordExportGuard guard =
        new WorkRecordExportGuard(
            allowedRateLimiter(),
            leases,
            new WorkRecordProductionProperties(),
            WorkRecordTelemetry.noop());

    try (WorkRecordExportGuard.Permit ignored = guard.acquire("tenant-1", user())) {
      assertThat(closed).isFalse();
    }

    assertThat(closed).isTrue();
  }

  private RateLimitService allowedRateLimiter() {
    return (key, limit, window) -> RateLimitDecision.allowed(limit - 1, 60);
  }

  private UserPrincipal user() {
    return new UserPrincipal(
        "user-1",
        "tenant-1",
        "alice",
        "Alice",
        Set.of("normal_user"),
        Set.of("work-record:export"),
        Map.of("work-record", DataScope.SELF));
  }

  private static final class RecordingTelemetry implements WorkRecordTelemetry {

    private String exportResult;

    @Override
    public void recordQuery(String operation, Duration duration) {}

    @Override
    public void recordExport(String result) {
      exportResult = result;
    }

    @Override
    public void recordPermissionDenied(String action) {}
  }
}
