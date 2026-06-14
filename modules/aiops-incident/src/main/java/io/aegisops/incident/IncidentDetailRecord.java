package io.aegisops.incident;

import java.util.List;

public record IncidentDetailRecord(
        IncidentRecord incident,
        List<IncidentAlertRecord> alerts,
        List<IncidentTimelineRecord> timeline
) {}
