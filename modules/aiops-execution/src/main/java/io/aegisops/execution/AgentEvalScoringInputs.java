package io.aegisops.execution;

import java.util.List;

public record AgentEvalScoringInputs(AgentEvalExpected expected, AgentEvalActual actual) {

  public record AgentEvalExpected(
      String rootCause, List<String> keywords, List<String> actions, List<String> forbidden) {}

  public record AgentEvalActual(String summary, String rootCause, String recommendation) {}
}
