package io.aegisops.runbook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aegisops.runbook.dto.AutomationPlanStepCreateCommand;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RunbookSafetyTest {
  @Test
  void generatedStepPayloadMustNotAllowExecution() {
    RunbookJson json = new RunbookJson(new ObjectMapper());

    AutomationPlanStepCreateCommand step =
        new AutomationPlanStepCreateCommand(
            "step_1",
            "plan_1",
            1,
            "Restart service",
            "shell",
            "service",
            json.write(
                Map.of(
                    "command", "systemctl restart app",
                    "dryRunOnly", true,
                    "executionAllowed", false)),
            "desc",
            "ok",
            "rollback",
            true,
            "pending");

    assertTrue(step.requiresApproval());
    assertFalse(step.actionPayloadJson().contains("\"executionAllowed\":true"));
    assertTrue(step.actionPayloadJson().contains("\"executionAllowed\":false"));
  }
}
