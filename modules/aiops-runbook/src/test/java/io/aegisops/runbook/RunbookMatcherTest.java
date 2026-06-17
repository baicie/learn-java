package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.runbook.dto.AlertForPlanRecord;
import io.aegisops.runbook.dto.IncidentForPlanRecord;
import io.aegisops.runbook.dto.RunbookRecord;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunbookMatcherTest {
  private final RunbookJson json = new RunbookJson(new ObjectMapper());
  private final RunbookMatcher matcher = new RunbookMatcher(json);

  @Test
  void matchesByKeywordSeverityAndFingerprint() {
    RunbookRecord runbook =
        new RunbookRecord(
            "rb_1",
            "tenant_1",
            "CPU high mitigation",
            "desc",
            "host",
            "medium",
            true,
            "{\"keywords\":[\"cpu\",\"load\"],\"severities\":[\"critical\"],\"fingerprints\":[\"fp_cpu\"]}",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    IncidentForPlanRecord incident =
        new IncidentForPlanRecord(
            "inc_1",
            "tenant_1",
            "CPU load high",
            "cpu usage high",
            "critical",
            "open",
            "asset_1",
            "CPU saturation",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            OffsetDateTime.now());

    AlertForPlanRecord alert =
        new AlertForPlanRecord(
            "alert_1", "critical", "CPU alert", "cpu > 90", "asset_1", "host-1", "fp_cpu", "{}");

    var result = matcher.match(incident, List.of(alert), null, null, List.of(runbook));

    assertEquals(1, result.size());
    assertTrue(result.get(0).score() >= 65);
    assertTrue(result.get(0).reasons().contains("fingerprint:fp_cpu"));
  }

  @Test
  void disabledRunbookIsIgnored() {
    RunbookRecord runbook =
        new RunbookRecord(
            "rb_1",
            "tenant_1",
            "CPU",
            "desc",
            "host",
            "medium",
            false,
            "{\"keywords\":[\"cpu\"]}",
            "{}",
            OffsetDateTime.now(),
            OffsetDateTime.now());

    IncidentForPlanRecord incident =
        new IncidentForPlanRecord(
            "inc_1",
            "tenant_1",
            "CPU high",
            "cpu high",
            "critical",
            "open",
            "asset_1",
            "",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            OffsetDateTime.now());

    assertTrue(matcher.match(incident, List.of(), null, null, List.of(runbook)).isEmpty());
  }
}
