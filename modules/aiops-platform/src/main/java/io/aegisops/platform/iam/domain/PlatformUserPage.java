package io.aegisops.platform.iam.domain;

import java.util.List;

public record PlatformUserPage(List<PlatformUser> items, long total, int page, int pageSize) {}
