from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="AIOPS_AGENT_", env_file=".env", extra="ignore")

    internal_token: str = "dev-internal-token"
    provider: str = "aiops-agent"
    model: str = "langgraph-deterministic"
    agent_name: str = "aegisops_diagnosis_graph"
    default_locale: str = "zh-CN"


settings = Settings()
