package io.aegisops.common.time;

import java.time.OffsetDateTime;

public class TimeProvider {
  public OffsetDateTime now() {
    return OffsetDateTime.now();
  }
}
