package io.aegisops.runner;

import io.aegisops.execution.dto.ExecutionRunRecord;
import io.aegisops.execution.dto.ExecutionStepRecord;
import java.util.List;

public interface ExecutionGrantValidator {
  void validate(ExecutionRunRecord run, List<ExecutionStepRecord> executionSteps);
}
