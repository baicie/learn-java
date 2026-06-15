package io.aegisops.ai.client;

import io.aegisops.common.exception.AppException;

public class AgentContractViolationException extends AppException {
    public AgentContractViolationException(String message) {
        super("AI_AGENT_CONTRACT_VIOLATION", message);
    }
}
