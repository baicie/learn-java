package io.aegisops.evidence;

public record EvidenceCollectResponse(
    String incidentId,
    int itemsMatched,
    int historyPoints,
    int trendPoints,
    int events,
    int triggers,
    int evidenceCreated,
    int evidenceUpdated) {}
