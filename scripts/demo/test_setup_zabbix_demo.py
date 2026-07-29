from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch


MODULE_PATH = Path(__file__).with_name("setup-zabbix-demo.py")
SPEC = importlib.util.spec_from_file_location("setup_zabbix_demo", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
demo = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(demo)


class FakeApi:
    def __init__(self, responses=None):
        self.responses = {key: list(value) for key, value in (responses or {}).items()}
        self.calls = []

    def call(self, method, params=None, auth=True):
        self.calls.append((method, params or {}, auth))
        queued = self.responses.get(method, [])
        if queued:
            response = queued.pop(0)
            if isinstance(response, Exception):
                raise response
            return response
        return {}

    def wait_ready(self):
        return None

    def login(self):
        return None


class ZabbixDemoProvisioningTest(unittest.TestCase):
    def require(self, name):
        self.assertTrue(hasattr(demo, name), f"missing expected demo API: {name}")
        return getattr(demo, name)

    def test_creates_trapper_item_without_http_agent_fields(self):
        api = FakeApi(
            {
                "item.get": [[]],
                "item.create": [{"itemids": ["item-1"]}],
            }
        )

        item_id = self.require("upsert_trapper_item")(
            api,
            "host-1",
            "CPU",
            "demo.cpu.util",
            self.require("VALUE_TYPE_FLOAT"),
            "127.0.0.1,zabbix-web",
        )

        self.assertEqual("item-1", item_id)
        _, payload, _ = next(call for call in api.calls if call[0] == "item.create")
        self.assertEqual(2, payload["type"])
        self.assertEqual("host-1", payload["hostid"])
        self.assertEqual("127.0.0.1,zabbix-web", payload["trapper_hosts"])
        self.assertNotIn("url", payload)
        self.assertNotIn("delay", payload)

    def test_updates_existing_item_without_create_only_hostid(self):
        api = FakeApi(
            {
                "item.get": [[{"itemid": "item-1"}]],
                "item.update": [{"itemids": ["item-1"]}],
            }
        )

        self.require("upsert_trapper_item")(
            api,
            "host-1",
            "CPU",
            "demo.cpu.util",
            self.require("VALUE_TYPE_FLOAT"),
            "zabbix-web",
        )

        _, payload, _ = next(call for call in api.calls if call[0] == "item.update")
        self.assertEqual("item-1", payload["itemid"])
        self.assertNotIn("hostid", payload)
        self.assertEqual(2, payload["type"])
        self.assertEqual(0, payload["status"])

    def test_reenables_existing_demo_host(self):
        api = FakeApi(
            {
                "host.get": [[{"hostid": "host-1"}]],
                "host.update": [{"hostids": ["host-1"]}],
            }
        )

        self.require("get_or_create_host")(api, "aiops-demo-host", "group-1")

        _, payload, _ = next(call for call in api.calls if call[0] == "host.update")
        self.assertEqual("host-1", payload["hostid"])
        self.assertEqual(0, payload["status"])

    def test_reenables_existing_demo_trigger(self):
        api = FakeApi(
            {
                "trigger.get": [[{"triggerid": "trigger-1"}]],
                "trigger.update": [{"triggerids": ["trigger-1"]}],
            }
        )

        self.require("upsert_trigger")(
            api,
            "host-1",
            "CPU high",
            "last(/aiops-demo-host/demo.cpu.util)>80",
            4,
        )

        _, payload, _ = next(call for call in api.calls if call[0] == "trigger.update")
        self.assertEqual("trigger-1", payload["triggerid"])
        self.assertEqual(0, payload["status"])

    def test_profiles_are_complete_and_recovery_is_healthy(self):
        values_for_action = self.require("values_for_action")
        item_specs = self.require("ITEM_SPECS")
        healthy = values_for_action("setup")
        incident = values_for_action("incident")
        cpu = values_for_action("cpu")

        self.assertEqual(set(item_specs), set(healthy))
        self.assertEqual(set(healthy), set(incident))
        self.assertEqual(set(healthy), set(cpu))
        self.assertEqual(healthy, values_for_action("recover"))
        self.assertEqual(healthy, values_for_action("reset"))
        self.assertEqual(95.0, incident["demo.cpu.util"])
        self.assertEqual(0, incident["demo.health.status"])
        self.assertEqual(12, incident["demo.error.count"])
        self.assertEqual(95.0, cpu["demo.cpu.util"])
        self.assertEqual(healthy["demo.health.status"], cpu["demo.health.status"])

    def test_history_push_retries_partial_errors_then_succeeds(self):
        api = FakeApi(
            {
                "history.push": [
                    {
                        "response": "success",
                        "data": [
                            {"itemid": "item-cpu"},
                            {"itemid": "item-health", "error": "not in cache"},
                        ],
                    },
                    {
                        "response": "success",
                        "data": [
                            {"itemid": "item-cpu"},
                            {"itemid": "item-health"},
                        ],
                    },
                ]
            }
        )
        sleeps = []

        self.require("push_history")(
            api,
            {
                "demo.cpu.util": "item-cpu",
                "demo.health.status": "item-health",
            },
            {"demo.cpu.util": 95.0, "demo.health.status": 0},
            max_attempts=2,
            retry_delay_seconds=0.25,
            sleep=sleeps.append,
        )

        pushes = [call for call in api.calls if call[0] == "history.push"]
        self.assertEqual(2, len(pushes))
        self.assertEqual([0.25], sleeps)
        self.assertEqual(
            [
                {"itemid": "item-cpu", "value": 95.0},
                {"itemid": "item-health", "value": 0},
            ],
            pushes[0][1],
        )

    def test_history_push_raises_after_bounded_partial_errors(self):
        api = FakeApi(
            {
                "history.push": [
                    {
                        "response": "success",
                        "data": [{"itemid": "item-cpu", "error": "denied"}],
                    },
                    {
                        "response": "success",
                        "data": [{"itemid": "item-cpu", "error": "denied"}],
                    },
                ]
            }
        )

        with self.assertRaisesRegex(RuntimeError, "item-cpu.*denied"):
            self.require("push_history")(
                api,
                {"demo.cpu.util": "item-cpu"},
                {"demo.cpu.util": 95.0},
                max_attempts=2,
                retry_delay_seconds=0,
                sleep=lambda _: None,
            )

        self.assertEqual(2, len([call for call in api.calls if call[0] == "history.push"]))

    def test_waits_until_all_triggers_reach_expected_values(self):
        api = FakeApi(
            {
                "trigger.get": [
                    [
                        {"triggerid": "trigger-cpu", "value": "1"},
                        {"triggerid": "trigger-health", "value": "0"},
                    ],
                    [
                        {"triggerid": "trigger-cpu", "value": "1"},
                        {"triggerid": "trigger-health", "value": "1"},
                    ],
                ]
            }
        )
        sleeps = []

        result = self.require("wait_for_trigger_values")(
            api,
            {"trigger-cpu": "1", "trigger-health": "1"},
            max_attempts=2,
            retry_delay_seconds=0.5,
            sleep=sleeps.append,
        )

        self.assertEqual("1", result["trigger-health"]["value"])
        self.assertEqual([0.5], sleeps)

    def test_disables_the_exact_legacy_web_scenario(self):
        api = FakeApi(
            {
                "httptest.get": [[{"httptestid": "web-1"}]],
                "httptest.update": [{"httptestids": ["web-1"]}],
            }
        )

        self.require("disable_legacy_web_scenario")(api, "host-1")

        _, payload, _ = next(call for call in api.calls if call[0] == "httptest.update")
        self.assertEqual({"httptestid": "web-1", "status": 1}, payload)

    def test_parser_accepts_all_supported_actions(self):
        parser = self.require("build_parser")()

        for action in (
            "setup",
            "incident",
            "recover",
            "reset",
            "cpu",
            "slow",
            "health-down",
            "error",
            "state",
        ):
            self.assertEqual(action, parser.parse_args(["--action", action]).action)

    def test_parser_limits_history_push_to_zabbix_web_by_default(self):
        with patch.dict(os.environ, {}, clear=True):
            args = self.require("build_parser")().parse_args([])

        self.assertEqual("auto", args.trapper_hosts)

    def test_auto_trapper_hosts_use_exact_compose_gateway_and_web_ip(self):
        inspect_result = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout=json.dumps(
                [
                    {
                        "Name": "/aegisops-zabbix-web",
                        "NetworkSettings": {
                            "Networks": {
                                "infra_default": {
                                    "IPAddress": "172.31.0.2",
                                    "Gateway": "172.31.0.1",
                                }
                            }
                        },
                    },
                    {
                        "Name": "/aegisops-zabbix-server",
                        "NetworkSettings": {
                            "Networks": {
                                "infra_default": {
                                    "IPAddress": "172.31.0.4",
                                    "Gateway": "172.31.0.1",
                                }
                            }
                        },
                    },
                ]
            ),
            stderr="",
        )

        with patch.object(
            demo.subprocess, "run", return_value=inspect_result
        ) as run:
            resolved = self.require("resolve_trapper_hosts")("auto")

        self.assertEqual("172.31.0.1,172.31.0.2", resolved)
        run.assert_called_once_with(
            [
                "docker",
                "inspect",
                "aegisops-zabbix-web",
                "aegisops-zabbix-server",
            ],
            check=True,
            capture_output=True,
            text=True,
        )

    def test_explicit_trapper_hosts_skip_docker_detection(self):
        with patch("subprocess.run") as run:
            resolved = self.require("resolve_trapper_hosts")(
                "10.0.0.10,10.0.0.11"
            )

        self.assertEqual("10.0.0.10,10.0.0.11", resolved)
        run.assert_not_called()

    def test_state_action_does_not_mutate_zabbix_configuration(self):
        trigger_rows = [
            [{"triggerid": f"trigger-{index}"}] for index in range(4)
        ]
        trigger_rows.append(
            [
                {
                    "triggerid": f"trigger-{index}",
                    "description": f"Trigger {index}",
                    "value": "0",
                }
                for index in range(4)
            ]
        )
        api = FakeApi(
            {
                "hostgroup.get": [[{"groupid": "group-1"}]],
                "host.get": [[{"hostid": "host-1", "host": "aiops-demo-host"}]],
                "item.get": [
                    [{"itemid": f"item-{index}"}] for index in range(6)
                ],
                "httptest.get": [[]],
                "trigger.get": trigger_rows,
                "problem.get": [[]],
            }
        )

        with (
            patch.object(demo, "ZabbixApi", return_value=api),
            patch.object(sys, "argv", ["setup-zabbix-demo.py", "--action", "state"]),
        ):
            demo.main()

        mutating_calls = [
            method
            for method, _, _ in api.calls
            if method.endswith(".create") or method.endswith(".update")
        ]
        self.assertEqual([], mutating_calls)


if __name__ == "__main__":
    unittest.main()
