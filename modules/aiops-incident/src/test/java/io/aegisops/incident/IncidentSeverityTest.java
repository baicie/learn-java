package io.aegisops.incident;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IncidentSeverityTest {
    @Test
    void normalizeUnknownSeverityToInfo() {
        assertEquals("info", IncidentSeverity.normalize(null));
        assertEquals("info", IncidentSeverity.normalize(""));
        assertEquals("info", IncidentSeverity.normalize("unknown"));
    }

    @Test
    void maxReturnsHigherSeverity() {
        assertEquals("critical", IncidentSeverity.max("warning", "critical"));
        assertEquals("disaster", IncidentSeverity.max("disaster", "critical"));
        assertEquals("warning", IncidentSeverity.max("info", "warning"));
    }

    @Test
    void maxCollectionReturnsHighestSeverity() {
        assertEquals("disaster", IncidentSeverity.max(List.of("info", "warning", "disaster", "critical")));
    }
}
