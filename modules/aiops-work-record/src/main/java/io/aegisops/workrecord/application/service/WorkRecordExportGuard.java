package io.aegisops.workrecord.application.service;

import io.aegisops.common.exception.AppException;
import io.aegisops.common.exception.ErrorCode;
import io.aegisops.security.DistributedLease;
import io.aegisops.security.DistributedLeaseService;
import io.aegisops.security.RateLimitDecision;
import io.aegisops.security.RateLimitService;
import io.aegisops.security.UserPrincipal;
import io.aegisops.workrecord.application.port.WorkRecordTelemetry;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class WorkRecordExportGuard {

  private final RateLimitService rateLimitService;
  private final DistributedLeaseService leaseService;
  private final WorkRecordProductionProperties properties;
  private final WorkRecordTelemetry telemetry;

  public WorkRecordExportGuard(
      RateLimitService rateLimitService,
      DistributedLeaseService leaseService,
      WorkRecordProductionProperties properties,
      WorkRecordTelemetry telemetry) {
    this.rateLimitService = rateLimitService;
    this.leaseService = leaseService;
    this.properties = properties;
    this.telemetry = telemetry;
  }

  public Permit acquire(String tenantId, UserPrincipal user) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId is required");
    }

    if (user == null || user.id() == null || user.id().isBlank()) {
      throw new AppException(
          ErrorCode.UNAUTHORIZED, "authenticated user is required");
    }

    String identity = tenantId + ":" + user.id();

    RateLimitDecision decision =
        rateLimitService.acquire(
            "work-record:export:" + identity,
            properties.getExport().getRequestsPerMinute(),
            Duration.ofMinutes(1));

    if (!decision.allowed()) {
      telemetry.recordExport("rate_limited");

      throw new AppException(
          ErrorCode.EXPORT_RATE_LIMITED,
          "too many export requests; retry after "
              + decision.retryAfterSeconds()
              + " seconds");
    }

    Optional<DistributedLease> lease =
        leaseService.tryAcquire(
            "work-record:export:" + identity,
            Duration.ofSeconds(properties.getExport().getLeaseSeconds()));

    if (lease.isEmpty()) {
      telemetry.recordExport("in_progress");

      throw new AppException(
          ErrorCode.EXPORT_IN_PROGRESS, "another export is already running");
    }

    return new Permit(lease.get());
  }

  public static final class Permit implements AutoCloseable {

    private final DistributedLease lease;

    private Permit(DistributedLease lease) {
      this.lease = lease;
    }

    @Override
    public void close() {
      lease.close();
    }
  }
}