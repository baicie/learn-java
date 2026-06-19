package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemActionItemCreateCommand;
import io.aegisops.execution.dto.PostmortemActionItemRecord;
import io.aegisops.execution.dto.PostmortemReportCreateCommand;
import io.aegisops.execution.dto.PostmortemReportRecord;
import io.aegisops.execution.dto.PostmortemSectionCreateCommand;
import io.aegisops.execution.dto.PostmortemSectionRecord;
import java.util.List;
import java.util.Optional;

public interface PostmortemRepository {
  void createReport(PostmortemReportCreateCommand command);

  void createSection(PostmortemSectionCreateCommand command);

  Optional<PostmortemReportRecord> findReport(String tenantId, String postmortemId);

  Optional<PostmortemReportRecord> findLatestByIncident(String tenantId, String incidentId);

  List<PostmortemSectionRecord> listSections(String tenantId, String postmortemId);

  void createActionItem(PostmortemActionItemCreateCommand command);

  List<PostmortemActionItemRecord> listActionItems(String tenantId, String postmortemId);

  Optional<PostmortemActionItemRecord> findActionItem(String tenantId, String actionItemId);

  boolean updateActionItemStatus(String tenantId, String actionItemId, String status);
}
