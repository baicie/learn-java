package io.aegisops.worker.outbox;

import io.aegisops.persistence.jooq.public_.tables.records.AutomationOutboxRecord;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory stand-in for {@link OutboxRepository} used by worker unit tests. */
final class NoopOutboxRepository implements OutboxRepository {

  final List<AutomationOutboxRecord> pending = new ArrayList<>();
  final Map<String, String> statuses = new HashMap<>();
  final Map<String, String> errorMessages = new HashMap<>();
  final List<String> markDoneCalls = new ArrayList<>();
  int recoverExpiredLeasesCalls;
  int extendLeaseCalls;

  @Override
  public List<AutomationOutboxRecord> claimNextPending(
      String targetApp, int batchSize, OffsetDateTime leaseUntil) {
    List<AutomationOutboxRecord> snapshot = new ArrayList<>(pending);
    pending.clear();
    snapshot.forEach(r -> statuses.put(r.getId(), "processing"));
    return snapshot;
  }

  @Override
  public int recoverExpiredLeases(String targetApp, OffsetDateTime now) {
    recoverExpiredLeasesCalls++;
    return 0;
  }

  @Override
  public boolean extendLease(String id, String targetApp, OffsetDateTime leaseUntil) {
    extendLeaseCalls++;
    return "processing".equals(statuses.get(id));
  }

  @Override
  public Optional<AutomationOutboxRecord> findById(String id) {
    return Optional.empty();
  }

  @Override
  public boolean markDone(String id, OffsetDateTime processedAt) {
    statuses.put(id, "done");
    markDoneCalls.add(id);
    return true;
  }

  @Override
  public boolean recordFailure(String id, String errorMessage) {
    statuses.put(id, "pending");
    errorMessages.put(id, errorMessage);
    return true;
  }

  @Override
  public boolean resetProcessing(String id) {
    statuses.put(id, "pending");
    return true;
  }
}
