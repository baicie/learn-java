package io.aegisops.platform.iam.domain;

import java.util.Map;
import java.util.Set;

public record UpdatePlatformUserData(
    String displayName, String email, Set<String> roleCodes, Map<String, String> patch) {}
