"""Agent settings - loaded from environment variables."""

from __future__ import annotations

from pydantic_settings import BaseSettings, SettingsConfigDict


class AgentSettings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", extra="ignore")

    evidence_api_base_url: str = "http://localhost:8080"
    knowledge_api_base_url: str = "http://localhost:8080"
    request_timeout_seconds: float = 5.0
    graph_version: str = "phase7.0-modular-graph"
    max_evidence_items: int = 8
    max_similar_cases: int = 5


settings = AgentSettings()
