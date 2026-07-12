package io.aegisops.platform.iam.domain;

import java.util.List;

public record ReplaceUserRolesCommand(List<String> roleCodes, String reason, int rowVersion) {}