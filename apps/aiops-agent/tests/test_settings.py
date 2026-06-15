from aiops_agent.settings import Settings


def test_agent_name_accepts_short_env_alias(monkeypatch):
    monkeypatch.setenv("AIOPS_AGENT_NAME", "custom_short_name")

    settings = Settings()

    assert settings.agent_name == "custom_short_name"


def test_agent_name_accepts_canonical_env_name(monkeypatch):
    monkeypatch.delenv("AIOPS_AGENT_NAME", raising=False)
    monkeypatch.setenv("AIOPS_AGENT_AGENT_NAME", "custom_canonical_name")

    settings = Settings()

    assert settings.agent_name == "custom_canonical_name"


def test_generation_mode_normalizes_unknown_value():
    settings = Settings(generation_mode="unknown")

    assert settings.normalized_generation_mode() == "deterministic"


def test_generation_mode_accepts_openai_compatible():
    settings = Settings(generation_mode="openai-compatible")

    assert settings.normalized_generation_mode() == "openai-compatible"
