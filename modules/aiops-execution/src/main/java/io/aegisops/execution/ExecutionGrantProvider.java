package io.aegisops.execution;

import io.aegisops.execution.dto.ExecutionRunCreateCommand;
import io.aegisops.execution.dto.ExecutionStepCreateCommand;
import java.util.List;

public interface ExecutionGrantProvider {
  IssuedExecutionGrant issue(
      ExecutionRunCreateCommand run, List<ExecutionStepCreateCommand> executionSteps);
}
