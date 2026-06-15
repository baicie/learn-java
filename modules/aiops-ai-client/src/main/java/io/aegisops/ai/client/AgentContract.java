package io.aegisops.ai.client;

public final class AgentContract {
    private AgentContract() {}

    public static final String DIAGNOSIS_CONTRACT_VERSION = "agent-diagnosis.v1";
    public static final String CONTRACT_VERSION_HEADER = "X-AegisOps-Contract-Version";
    public static final String TRACE_ID_HEADER = "X-AegisOps-Trace-Id";
    public static final String INTERNAL_TOKEN_HEADER = "X-AegisOps-Internal-Token";
}
