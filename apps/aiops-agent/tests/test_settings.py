from aiops_agent.settings import Settings


def test_static_service_token_settings_do_not_exist():
    settings = Settings()

    assert not hasattr(settings, "inbound_oauth2_issuer")
    assert not hasattr(settings, "inbound_oauth2_jwks_url")
    assert not hasattr(settings, "inbound_oauth2_audience")
    assert not hasattr(settings, "outbound_oauth2_token_url")
    assert not hasattr(settings, "outbound_oauth2_client_id")
    assert not hasattr(settings, "outbound_oauth2_client_secret")
    assert not hasattr(settings, "outbound_oauth2_scope")
    assert not hasattr(settings, "internal_agent_token")
    assert not hasattr(settings, "outbound_static_token")
    assert not hasattr(settings, "inbound_auth_mode")
    assert not hasattr(settings, "outbound_auth_mode")


def test_diagnosis_grant_cannot_be_disabled_by_configuration():
    settings = Settings()

    assert not hasattr(settings, "diagnosis_grant_required")


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


def test_generation_mode_accepts_phase7_workflow():
    settings = Settings(generation_mode="workflow")

    assert settings.normalized_generation_mode() == "workflow"


def test_openai_base_url_trims_trailing_slashes():
    settings = Settings(openai_base_url="https://example.com/v1///")

    assert settings.normalized_openai_base_url() == "https://example.com/v1"


def test_openai_response_format_enabled_defaults_true():
    settings = Settings()

    assert settings.openai_response_format_enabled is True


def test_work_record_provider_defaults_to_deterministic():
    settings = Settings()

    assert settings.normalized_work_record_provider() == "deterministic"
    assert (
        settings.dify_work_record_workflow_version
        == "work-record-2026-07-19.1"
    )


def test_work_record_provider_accepts_dify_configuration(monkeypatch):
    monkeypatch.setenv("AIOPS_AGENT_WORK_RECORD_PROVIDER", "DIFY")
    monkeypatch.setenv("AIOPS_AGENT_DIFY_BASE_URL", "https://dify.example.com/v1/")
    monkeypatch.setenv("AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY", "secret-key")
    monkeypatch.setenv("AIOPS_AGENT_DIFY_WORK_RECORD_WORKFLOW_ID", "published-42")
    monkeypatch.setenv(
        "AIOPS_AGENT_DIFY_WORK_RECORD_WORKFLOW_VERSION",
        "work-record-2026-07-19.1",
    )
    monkeypatch.setenv("AIOPS_AGENT_DIFY_TIMEOUT_SECONDS", "75")
    monkeypatch.setenv("AIOPS_AGENT_DIFY_MAX_RETRIES", "2")
    monkeypatch.setenv("AIOPS_AGENT_DIFY_MAX_INPUT_BYTES", "65536")
    monkeypatch.setenv("AIOPS_AGENT_DIFY_USER_HMAC_SECRET", "hmac-secret")

    settings = Settings()

    assert settings.normalized_work_record_provider() == "dify"
    assert settings.normalized_dify_base_url() == "https://dify.example.com/v1"
    assert settings.dify_work_record_api_key == "secret-key"
    assert settings.dify_work_record_workflow_id == "published-42"
    assert settings.dify_work_record_workflow_version == "work-record-2026-07-19.1"
    assert settings.dify_timeout_seconds == 75
    assert settings.dify_max_retries == 2
    assert settings.dify_max_input_bytes == 65536
    assert settings.dify_user_hmac_secret == "hmac-secret"


def test_work_record_provider_normalizes_unknown_value():
    settings = Settings(work_record_provider="unknown")

    assert settings.normalized_work_record_provider() == "deterministic"
