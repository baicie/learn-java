package io.aegisops.demo.order;

public record FaultStateResponse(
    boolean slowApiEnabled,
    long slowApiDelayMs,
    boolean cpuHighEnabled,
    int cpuWorkers,
    boolean healthy,
    boolean errorLogEnabled,
    long errorCount,
    double simulatedCpuUtil,
    double simulatedMemoryUtil,
    double simulatedLoadAvg,
    double simulatedOrderLatencySeconds) {}
