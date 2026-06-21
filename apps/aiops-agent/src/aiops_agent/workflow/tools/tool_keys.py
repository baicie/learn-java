"""Built-in agent tool keys used by the plugin allowlist."""

EVIDENCE_FETCH = "evidence.fetch"
KNOWLEDGE_SEARCH_CASES = "knowledge.search_cases"
CHECKPOINT_CREATE = "checkpoint.create"
CHECKPOINT_GET = "checkpoint.get"
MEMORY_SEARCH = "memory.search"
MEMORY_CREATE = "memory.create"

ALL_TOOL_KEYS = frozenset(
    {
        EVIDENCE_FETCH,
        KNOWLEDGE_SEARCH_CASES,
        CHECKPOINT_CREATE,
        CHECKPOINT_GET,
        MEMORY_SEARCH,
        MEMORY_CREATE,
    }
)
