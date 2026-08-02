from __future__ import annotations

import json
import os
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILE = ROOT / "deploy/docker-compose.zabbix.yml"


def compose() -> dict[str, object]:
    return yaml.safe_load(COMPOSE_FILE.read_text(encoding="utf-8"))


def test_zabbix_host_ports_default_to_loopback_only():
    services = compose()["services"]

    server_port = services["zabbix-server"]["ports"][0]
    web_port = services["zabbix-web"]["ports"][0]

    assert server_port["host_ip"] == "${ZABBIX_BIND_ADDRESS:-127.0.0.1}"
    assert server_port["published"] == "${ZABBIX_SERVER_PORT:-10051}"
    assert server_port["target"] == 10051
    assert web_port["host_ip"] == "${ZABBIX_BIND_ADDRESS:-127.0.0.1}"
    assert web_port["published"] == "${ZABBIX_WEB_PORT:-8083}"
    assert web_port["target"] == 8080


def test_legacy_numeric_port_environment_still_resolves_to_loopback():
    if shutil.which("docker") is None:
        pytest.skip("docker compose is required")

    compose_env = {
        **os.environ,
        "ZABBIX_DB_PASSWORD": "compose-contract-test-password",
        "ZABBIX_WEB_PORT": "8083",
        "ZABBIX_SERVER_PORT": "10051",
    }
    compose_env.pop("ZABBIX_BIND_ADDRESS", None)
    result = subprocess.run(
        [
            "docker",
            "compose",
            "-f",
            str(COMPOSE_FILE),
            "config",
            "--format",
            "json",
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        env=compose_env,
    )
    services = json.loads(result.stdout)["services"]

    assert services["zabbix-server"]["ports"][0]["host_ip"] == "127.0.0.1"
    assert services["zabbix-web"]["ports"][0]["host_ip"] == "127.0.0.1"


def test_only_zabbix_web_joins_external_app_api_network():
    model = compose()

    assert set(model["services"]["zabbix-web"]["networks"]) == {
        "default",
        "zabbix-api",
    }
    assert model["services"]["zabbix-web"]["networks"]["zabbix-api"] == {
        "aliases": ["aegisops-zabbix-web"]
    }
    for service_name in ("zabbix-postgres", "zabbix-server", "zabbix-agent2"):
        assert "networks" not in model["services"][service_name]
    assert model["networks"]["zabbix-api"] == {
        "external": True,
        "name": "aegisops-zabbix-api",
    }


def test_zabbix_agent2_shares_server_loopback_for_default_host():
    agent = compose()["services"]["zabbix-agent2"]

    assert agent["network_mode"] == "service:zabbix-server"
    assert agent["environment"] == {
        "ZBX_HOSTNAME": "Zabbix server",
        "ZBX_SERVER_HOST": "127.0.0.1",
        "ZBX_SERVER_ACTIVE": "127.0.0.1",
    }
