package io.aegisops.server.dispatch;

import io.aegisops.common.outbox.OutboxWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Server-side helper that hands work off to the {@code worker} process via the {@code
 * automation_outbox} table.
 *
 * <p>Three jobs are dispatched today:
 *
 * <ul>
 *   <li>{@code incident-aggregate} — roll alert events into incidents (Phase 2)
 *   <li>{@code rca-diagnosis} — run rule-based RCA and AI diagnosis (Phase 3/4)
 *   <li>{@code postmortem-draft} — generate the postmortem draft after an execution run completes
 *       (Phase 6)
 * </ul>
 *
 * <p>The matching {@code OutboxJob} skeleton implementations live in {@code apps/aiops-worker.job}.
 * They are intentionally no-ops for now; real logic lands in the dedicated phase.
 */
@Service
public class IncidentPostmortemDispatcher {

  private static final String TARGET_APP = "worker";

  private final OutboxWriter outboxWriter;

  public IncidentPostmortemDispatcher(OutboxWriter outboxWriter) {
    this.outboxWriter = outboxWriter;
  }

  /** Enqueue an {@code incident-aggregate} job for the given tenant and trigger source. */
  public String dispatchIncidentAggregation(
      String tenantId, String triggerSource, String sourceRef) {
    Map<String, Object> payload = basePayload(tenantId, triggerSource, sourceRef);
    payload.put("trigger", "incident-aggregate");
    return outboxWriter.enqueue(TARGET_APP, "incident-aggregate", payload);
  }

  /** Enqueue an {@code rca-diagnosis} job for the given incident. */
  public String dispatchRcaDiagnosis(String tenantId, String incidentId) {
    Map<String, Object> payload = basePayload(tenantId, "incident:" + incidentId, incidentId);
    payload.put("incidentId", incidentId);
    payload.put("trigger", "rca-diagnosis");
    return outboxWriter.enqueue(TARGET_APP, "rca-diagnosis", payload);
  }

  /** Enqueue a {@code postmortem-draft} job after an execution run completes. */
  public String dispatchPostmortemDraft(String tenantId, String incidentId, String executionId) {
    Map<String, Object> payload = basePayload(tenantId, "execution:" + executionId, executionId);
    payload.put("incidentId", incidentId);
    payload.put("executionId", executionId);
    payload.put("trigger", "postmortem-draft");
    return outboxWriter.enqueue(TARGET_APP, "postmortem-draft", payload);
  }

  private Map<String, Object> basePayload(String tenantId, String triggerSource, String sourceRef) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("tenantId", tenantId);
    payload.put("triggerSource", triggerSource);
    payload.put("sourceRef", sourceRef);
    return payload;
  }
}
