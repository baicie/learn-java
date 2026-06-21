package io.aegisops.ai.client;

import io.aegisops.ai.client.dto.AgentDiagnosisRequest;
import io.aegisops.ai.client.dto.AgentDiagnosisResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AgentContractValidator {
  public void validateRequest(AgentDiagnosisRequest request) {
    if (request == null) {
      throw violation("request is null");
    }

    requireEquals(
        "contractVersion", AgentContract.DIAGNOSIS_CONTRACT_VERSION, request.contractVersion());
    requireText("tenantId", request.tenantId());
    requireText("incidentId", request.incidentId());
    requireText("locale", request.locale());
    requireText("traceId", request.traceId());

    if (request.incident() == null) {
      throw violation("incident is required");
    }

    requireText("incident.id", request.incident().id());

    if (request.incident().alertCount() < 0) {
      throw violation("incident.alertCount must be >= 0");
    }

    if (request.alerts() == null) {
      throw violation("alerts is required");
    }

    for (int i = 0; i < request.alerts().size(); i++) {
      if (request.alerts().get(i) == null) {
        throw violation("alerts[" + i + "] is null");
      }
      requireText("alerts[" + i + "].id", request.alerts().get(i).id());
    }

    if (request.rca() != null) {
      requireText("rca.id", request.rca().id());
    }
  }

  public void validateResponse(AgentDiagnosisResponse response) {
    if (response == null) {
      throw violation("response is null");
    }

    requireEquals(
        "contractVersion", AgentContract.DIAGNOSIS_CONTRACT_VERSION, response.contractVersion());
    requireText("provider", response.provider());
    requireText("model", response.model());
    requireText("agentName", response.agentName());
    requireText("summary", response.summary());
    requireText("rootCause", response.rootCause());
    requireText("impact", response.impact());
    requireList("nextSteps", response.nextSteps());
    requireList("runbookSuggestions", response.runbookSuggestions());
    requireList("risks", response.risks());

    if (response.raw() == null) {
      throw violation("raw is required");
    }

    validateNoUnsafeAutoExecution(response);
  }

  private void validateNoUnsafeAutoExecution(AgentDiagnosisResponse response) {
    List<String> merged = new ArrayList<>();
    if (response.nextSteps() != null) merged.addAll(response.nextSteps());
    if (response.runbookSuggestions() != null) merged.addAll(response.runbookSuggestions());
    if (response.risks() != null) merged.addAll(response.risks());
    String joined = String.join("\n", merged).toLowerCase();

    String[] forbidden = {
      "rm -rf",
      "drop database",
      "truncate table",
      "delete namespace",
      "kubectl delete",
      "format disk",
      "shutdown -h",
      "reboot now",
      "无需审批自动",
      "自动执行删除",
      "自动清空"
    };

    for (String keyword : forbidden) {
      if (joined.contains(keyword)) {
        throw violation("response contains forbidden unsafe action keyword: " + keyword);
      }
    }
  }

  private void requireText(String field, String value) {
    if (value == null || value.isBlank()) {
      throw violation(field + " is required");
    }
  }

  private void requireList(String field, Collection<String> value) {
    if (value == null) {
      throw violation(field + " is required");
    }

    for (String item : value) {
      if (item == null) {
        throw violation(field + " contains null item");
      }
    }
  }

  private void requireEquals(String field, String expected, String actual) {
    if (!expected.equals(actual)) {
      throw violation(field + " must be " + expected);
    }
  }

  private AgentContractViolationException violation(String message) {
    return new AgentContractViolationException(message);
  }
}
