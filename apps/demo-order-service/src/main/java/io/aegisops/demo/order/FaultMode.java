package io.aegisops.demo.order;

public record FaultMode(
    Boolean slowApiEnabled,
    Long slowApiDelayMs,
    Boolean cpuHighEnabled,
    Integer cpuWorkers,
    Boolean healthy,
    Boolean errorLogEnabled) {

  public static FaultMode incident() {
    return new FaultMode(true, 2500L, true, 2, false, true);
  }

  public static FaultMode recover() {
    return new FaultMode(false, 0L, false, 0, true, false);
  }
}
