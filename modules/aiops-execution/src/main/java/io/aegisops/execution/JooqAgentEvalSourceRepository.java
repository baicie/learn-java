package io.aegisops.execution;

import static io.aegisops.persistence.jooq.Tables.AI_DIAGNOSIS;

import io.aegisops.execution.dto.AgentDiagnosisSnapshot;
import io.aegisops.execution.dto.IncidentCaseResponse;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqAgentEvalSourceRepository implements AgentEvalSourceRepository {
  private final DSLContext dsl;
  private final IncidentCaseService incidentCaseService;

  public JooqAgentEvalSourceRepository(DSLContext dsl, IncidentCaseService incidentCaseService) {
    this.dsl = dsl;
    this.incidentCaseService = incidentCaseService;
  }

  @Override
  public Optional<AgentDiagnosisSnapshot> findLatestDiagnosis(String tenantId, String incidentId) {
    return dsl.select(
            AI_DIAGNOSIS.ID,
            AI_DIAGNOSIS.INCIDENT_ID,
            AI_DIAGNOSIS.SUMMARY,
            AI_DIAGNOSIS.ROOT_CAUSE,
            AI_DIAGNOSIS.NEXT_STEPS,
            AI_DIAGNOSIS.CREATED_AT)
        .from(AI_DIAGNOSIS)
        .where(AI_DIAGNOSIS.TENANT_ID.eq(tenantId))
        .and(AI_DIAGNOSIS.INCIDENT_ID.eq(incidentId))
        .orderBy(AI_DIAGNOSIS.CREATED_AT.desc())
        .limit(1)
        .fetchOptional(
            record ->
                new AgentDiagnosisSnapshot(
                    record.get(AI_DIAGNOSIS.ID),
                    record.get(AI_DIAGNOSIS.INCIDENT_ID),
                    record.get(AI_DIAGNOSIS.SUMMARY),
                    record.get(AI_DIAGNOSIS.ROOT_CAUSE),
                    record.get(AI_DIAGNOSIS.NEXT_STEPS) == null
                        ? null
                        : record.get(AI_DIAGNOSIS.NEXT_STEPS).toString(),
                    record.get(AI_DIAGNOSIS.CREATED_AT)));
  }

  @Override
  public IncidentCaseResponse getIncidentCase(String tenantId, String caseId) {
    return incidentCaseService.get(tenantId, caseId);
  }
}
