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

  @Override
  public List<AutomationOutboxRecord> claimNextPending(String targetApp, int batchSize) {
    List<AutomationOutboxRecord> snapshot = new ArrayList<>(pending);
    pending.clear();
    snapshot.forEach(r -> statuses.put(r.getId(), "processing"));
    return snapshot;
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
