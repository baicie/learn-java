package io.aegisops.execution;

import io.aegisops.execution.dto.PostmortemSourceBundle;
import java.util.Optional;

public interface PostmortemSourceRepository {
  Optional<PostmortemSourceBundle> load(String tenantId, String incidentId);
}
