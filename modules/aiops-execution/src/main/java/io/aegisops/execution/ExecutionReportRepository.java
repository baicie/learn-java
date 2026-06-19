package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionAuditEventCreateCommand;
import io.aegisops.execution.dto.ExecutionAuditEventRecord;
import io.aegisops.execution.dto.ExecutionReportCreateCommand;
import io.aegisops.execution.dto.ExecutionReportRecord;
import io.aegisops.execution.dto.ExecutionReportSectionCreateCommand;
import io.aegisops.execution.dto.ExecutionReportSectionRecord;
import io.aegisops.execution.dto.ExecutionVerificationCreateCommand;
import io.aegisops.execution.dto.ExecutionVerificationRecord;
import java.util.List;
import java.util.Optional;

public interface ExecutionReportRepository {
  void createReport(ExecutionReportCreateCommand command);

  void createSection(ExecutionReportSectionCreateCommand command);

  Optional<ExecutionReportRecord> findReport(String tenantId, String reportId);

  Optional<ExecutionReportRecord> findLatestReportByExecution(String tenantId, String executionId);

  List<ExecutionReportSectionRecord> listSections(String tenantId, String reportId);

  void createVerification(ExecutionVerificationCreateCommand command);

  List<ExecutionVerificationRecord> listVerifications(String tenantId, String executionId);

  void createAuditEvent(ExecutionAuditEventCreateCommand command);

  List<ExecutionAuditEventRecord> listAuditEvents(String tenantId, String executionId);
}
