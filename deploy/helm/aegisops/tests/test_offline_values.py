from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(name: str) -> dict:
    with (ROOT / name).open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def test_offline_values_enables_offline_mode_and_local_registry():
    values = load_yaml("values-offline.yaml")

    assert values["offline"]["enabled"] is True
    assert values["global"]["imageRegistry"] == "registry.local/aegisops"


def test_offline_values_uses_local_image_repositories():
    values = load_yaml("values-offline.yaml")

    apps = values["apps"]
    assert apps["app"]["image"]["repository"] == "aegisops-app"
    assert apps["runner"]["image"]["repository"] == "aiops-runner"
    assert apps["agent"]["image"]["repository"] == "aiops-agent"
