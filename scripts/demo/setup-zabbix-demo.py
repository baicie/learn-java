#!/usr/bin/env python3
"""Provision and drive the real local Zabbix demo with trapper values."""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request


ZABBIX_TRAPPER_TYPE = 2
VALUE_TYPE_FLOAT = 0
VALUE_TYPE_UINT = 3
LEGACY_WEB_SCENARIO = "AegisOps order-service scenario"
SUPPORTED_ACTIONS = (
    "setup",
    "incident",
    "recover",
    "reset",
    "cpu",
    "slow",
    "health-down",
    "error",
    "state",
)

COMMON_TAGS = [
    {"tag": "app", "value": "mall"},
    {"tag": "env", "value": "demo"},
    {"tag": "service", "value": "order-service"},
]

ITEM_SPECS = {
    "demo.cpu.util": {
        "name": "AegisOps Demo CPU Utilization",
        "value_type": VALUE_TYPE_FLOAT,
    },
    "demo.memory.util": {
        "name": "AegisOps Demo Memory Utilization",
        "value_type": VALUE_TYPE_FLOAT,
    },
    "demo.load.avg": {
        "name": "AegisOps Demo Load Average",
        "value_type": VALUE_TYPE_FLOAT,
    },
    "demo.health.status": {
        "name": "AegisOps Demo Health Status",
        "value_type": VALUE_TYPE_UINT,
    },
    "demo.order.create.time": {
        "name": "AegisOps Demo Order Create Time",
        "value_type": VALUE_TYPE_FLOAT,
    },
    "demo.error.count": {
        "name": "AegisOps Demo Error Count",
        "value_type": VALUE_TYPE_UINT,
    },
}

HEALTHY_VALUES = {
    "demo.cpu.util": 20.0,
    "demo.memory.util": 40.0,
    "demo.load.avg": 0.5,
    "demo.health.status": 1,
    "demo.order.create.time": 0.1,
    "demo.error.count": 0,
}

ACTION_OVERRIDES = {
    "setup": {},
    "recover": {},
    "reset": {},
    "incident": {
        "demo.cpu.util": 95.0,
        "demo.memory.util": 85.0,
        "demo.load.avg": 6.0,
        "demo.health.status": 0,
        "demo.order.create.time": 2.5,
        "demo.error.count": 12,
    },
    "cpu": {"demo.cpu.util": 95.0},
    "slow": {"demo.order.create.time": 2.5},
    "health-down": {"demo.health.status": 0},
    "error": {"demo.error.count": 12},
}

TRIGGER_SPECS = {
    "demo.cpu.util": {
        "description": "AegisOps Demo CPU High",
        "condition": ">80",
        "priority": 4,
    },
    "demo.order.create.time": {
        "description": "AegisOps Demo API Slow",
        "condition": ">2",
        "priority": 3,
    },
    "demo.health.status": {
        "description": "AegisOps Demo Health Check Failed",
        "condition": "=0",
        "priority": 5,
    },
    "demo.error.count": {
        "description": "AegisOps Demo Error Log Increased",
        "condition": ">0",
        "priority": 3,
    },
}

ACTIVE_TRIGGER_KEYS = {
    "setup": set(),
    "recover": set(),
    "reset": set(),
    "incident": set(TRIGGER_SPECS),
    "cpu": {"demo.cpu.util"},
    "slow": {"demo.order.create.time"},
    "health-down": {"demo.health.status"},
    "error": {"demo.error.count"},
}


class ZabbixApi:
    def __init__(self, url: str, username: str, password: str):
        self.url = url
        self.username = username
        self.password = password
        self.auth = None
        self.request_id = 1

    def call(self, method, params=None, auth=True):
        payload = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params if params is not None else {},
            "id": self.request_id,
        }
        self.request_id += 1

        if auth and self.auth:
            payload["auth"] = self.auth

        request = urllib.request.Request(
            self.url,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json-rpc"},
            method="POST",
        )

        with urllib.request.urlopen(request, timeout=10) as response:
            result = json.loads(response.read().decode("utf-8"))

        if "error" in result:
            raise RuntimeError(f"Zabbix API error for {method}: {result['error']}")

        return result.get("result")

    def wait_ready(self, timeout_seconds=180):
        deadline = time.time() + timeout_seconds
        last_error = None

        while time.time() < deadline:
            try:
                version = self.call("apiinfo.version", {}, auth=False)
                print(f"Zabbix API is ready, version={version}")
                return
            except Exception as exc:
                last_error = exc
                time.sleep(3)

        raise RuntimeError(f"Zabbix API is not ready: {last_error}")

    def login(self):
        try:
            self.auth = self.call(
                "user.login",
                {"username": self.username, "password": self.password},
                auth=False,
            )
        except RuntimeError:
            self.auth = self.call(
                "user.login",
                {"user": self.username, "password": self.password},
                auth=False,
            )

        print("Logged in to Zabbix API")


def first(items):
    return items[0] if items else None


def get_or_create_group(api, name):
    group = first(api.call("hostgroup.get", {"filter": {"name": [name]}}))

    if group:
        print(f"Host group exists: {name} ({group['groupid']})")
        return group["groupid"]

    created = api.call("hostgroup.create", {"name": name})
    groupid = created["groupids"][0]
    print(f"Created host group: {name} ({groupid})")
    return groupid


def get_or_create_host(api, host, groupid):
    found = first(api.call("host.get", {"filter": {"host": [host]}}))
    payload = {
        "host": host,
        "name": "AegisOps Demo Host",
        "groups": [{"groupid": groupid}],
        "tags": COMMON_TAGS,
        "status": 0,
    }

    if found:
        hostid = found["hostid"]
        api.call("host.update", {"hostid": hostid, **payload})
        print(f"Updated host: {host} ({hostid})")
        return hostid

    created = api.call("host.create", payload)
    hostid = created["hostids"][0]
    print(f"Created host: {host} ({hostid})")
    return hostid


def get_item(api, hostid, key):
    items = api.call("item.get", {"hostids": [hostid], "filter": {"key_": [key]}})
    return first(items)


def upsert_trapper_item(api, hostid, name, key, value_type, trapper_hosts):
    update_payload = {
        "name": name,
        "key_": key,
        "type": ZABBIX_TRAPPER_TYPE,
        "value_type": value_type,
        "trapper_hosts": trapper_hosts,
        "history": "1d",
        "trends": "7d",
        "tags": COMMON_TAGS,
        "status": 0,
    }
    existing = get_item(api, hostid, key)

    if existing:
        itemid = existing["itemid"]
        api.call("item.update", {"itemid": itemid, **update_payload})
        print(f"Updated trapper item: {name} ({key})")
        return itemid

    created = api.call("item.create", {"hostid": hostid, **update_payload})
    itemid = created["itemids"][0]
    print(f"Created trapper item: {name} ({key})")
    return itemid


def get_trigger(api, hostid, description):
    triggers = api.call(
        "trigger.get",
        {"hostids": [hostid], "filter": {"description": [description]}},
    )
    return first(triggers)


def upsert_trigger(api, hostid, description, expression, priority):
    payload = {
        "description": description,
        "expression": expression,
        "priority": priority,
        "tags": COMMON_TAGS,
        "status": 0,
    }
    existing = get_trigger(api, hostid, description)

    if existing:
        triggerid = existing["triggerid"]
        api.call("trigger.update", {"triggerid": triggerid, **payload})
        print(f"Updated trigger: {description}")
        return triggerid

    created = api.call("trigger.create", payload)
    triggerid = created["triggerids"][0]
    print(f"Created trigger: {description}")
    return triggerid


def disable_legacy_web_scenario(api, hostid):
    tests = api.call(
        "httptest.get",
        {"hostids": [hostid], "filter": {"name": [LEGACY_WEB_SCENARIO]}},
    )
    legacy = first(tests)
    if not legacy:
        return False

    api.call("httptest.update", {"httptestid": legacy["httptestid"], "status": 1})
    print(f"Disabled legacy web scenario: {LEGACY_WEB_SCENARIO}")
    return True


def values_for_action(action):
    if action not in ACTION_OVERRIDES:
        raise ValueError(f"Action does not push history values: {action}")

    values = dict(HEALTHY_VALUES)
    values.update(ACTION_OVERRIDES[action])
    return values


def _history_push_errors(result, payload):
    if not isinstance(result, dict) or result.get("response") != "success":
        return [f"unexpected response: {result!r}"]

    data = result.get("data")
    if not isinstance(data, list):
        return [f"response data is not a list: {data!r}"]

    errors = []
    for index, entry in enumerate(data):
        if not isinstance(entry, dict):
            errors.append(f"entry {index}: invalid result {entry!r}")
        elif entry.get("error"):
            itemid = entry.get("itemid", payload[index].get("itemid", "unknown"))
            errors.append(f"{itemid}: {entry['error']}")

    if len(data) != len(payload):
        errors.append(f"expected {len(payload)} item results, got {len(data)}")
    return errors


def push_history(
    api,
    item_ids,
    values,
    max_attempts=19,
    retry_delay_seconds=5,
    sleep=time.sleep,
):
    if max_attempts < 1:
        raise ValueError("max_attempts must be at least 1")

    missing = set(values) - set(item_ids)
    if missing:
        raise ValueError(f"Missing item ids for: {', '.join(sorted(missing))}")

    payload = [
        {"itemid": item_ids[key], "value": value} for key, value in values.items()
    ]
    last_errors = []

    for attempt in range(1, max_attempts + 1):
        result = api.call("history.push", payload)
        last_errors = _history_push_errors(result, payload)
        if not last_errors:
            print(f"Pushed {len(payload)} Zabbix history values")
            return result

        if attempt < max_attempts:
            print(
                f"history.push attempt {attempt}/{max_attempts} was not accepted: "
                + "; ".join(last_errors)
            )
            sleep(retry_delay_seconds)

    raise RuntimeError(
        f"history.push failed after {max_attempts} attempts: " + "; ".join(last_errors)
    )


def expected_trigger_values(action, trigger_ids):
    active_keys = ACTIVE_TRIGGER_KEYS[action]
    return {
        trigger_ids[key]: "1" if key in active_keys else "0"
        for key in TRIGGER_SPECS
    }


def wait_for_trigger_values(
    api,
    expected_values,
    max_attempts=30,
    retry_delay_seconds=1,
    sleep=time.sleep,
):
    if max_attempts < 1:
        raise ValueError("max_attempts must be at least 1")
    if not expected_values:
        return {}

    last_by_id = {}
    triggerids = list(expected_values)
    for attempt in range(1, max_attempts + 1):
        triggers = api.call(
            "trigger.get",
            {
                "output": ["triggerid", "description", "value", "lastchange"],
                "triggerids": triggerids,
            },
        )
        last_by_id = {trigger["triggerid"]: trigger for trigger in triggers}
        if all(
            last_by_id.get(triggerid, {}).get("value") == expected
            for triggerid, expected in expected_values.items()
        ):
            return last_by_id

        if attempt < max_attempts:
            sleep(retry_delay_seconds)

    actual = {
        triggerid: last_by_id.get(triggerid, {}).get("value", "missing")
        for triggerid in expected_values
    }
    raise RuntimeError(
        f"Zabbix triggers did not reach expected values {expected_values}; actual={actual}"
    )


def print_state(api, trigger_ids):
    triggers = api.call(
        "trigger.get",
        {
            "output": ["triggerid", "description", "value", "lastchange", "priority"],
            "triggerids": list(trigger_ids.values()),
            "sortfield": "description",
        },
    )
    print("Trigger state:")
    for trigger in triggers:
        state = "PROBLEM" if trigger.get("value") == "1" else "OK"
        print(f"  {state:7} {trigger.get('description')} ({trigger.get('triggerid')})")

    problems = api.call(
        "problem.get",
        {
            "output": ["eventid", "objectid", "name", "severity", "clock"],
            "objectids": list(trigger_ids.values()),
            "sortfield": ["eventid"],
            "sortorder": "DESC",
        },
    )
    print(f"Open problems: {len(problems)}")
    for problem in problems:
        print(
            f"  event={problem.get('eventid')} trigger={problem.get('objectid')} "
            f"severity={problem.get('severity')} name={problem.get('name')}"
        )


def resolve_trapper_hosts(configured_hosts):
    if configured_hosts != "auto":
        return configured_hosts

    try:
        result = subprocess.run(
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
        containers = json.loads(result.stdout)
        web_networks = containers[0]["NetworkSettings"]["Networks"]
        server_networks = containers[1]["NetworkSettings"]["Networks"]
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError, KeyError, IndexError) as exc:
        raise RuntimeError(
            "Cannot auto-detect Zabbix trapper allowed hosts; start the local "
            "Compose stack or set AIOPS_ZABBIX_TRAPPER_HOSTS explicitly"
        ) from exc

    for network_name in sorted(set(web_networks) & set(server_networks)):
        web_network = web_networks[network_name]
        server_network = server_networks[network_name]
        gateway = web_network.get("Gateway")
        web_ip = web_network.get("IPAddress")
        if gateway and web_ip and server_network.get("IPAddress"):
            resolved = f"{gateway},{web_ip}"
            print(f"Auto-detected Zabbix trapper allowed hosts: {resolved}")
            return resolved

    raise RuntimeError(
        "Cannot find a shared Docker network for aegisops-zabbix-web and "
        "aegisops-zabbix-server; set AIOPS_ZABBIX_TRAPPER_HOSTS explicitly"
    )


def build_parser():
    parser = argparse.ArgumentParser(
        description="Provision Zabbix trapper items and inject a deterministic demo state."
    )
    parser.add_argument(
        "--action",
        choices=SUPPORTED_ACTIONS,
        default="setup",
        help="Operation to perform (state only queries existing demo objects)",
    )
    parser.add_argument(
        "--zabbix-url",
        default=os.getenv(
            "AIOPS_ZABBIX_URL", "http://localhost:8081/api_jsonrpc.php"
        ),
        help="Zabbix API URL from the host machine",
    )
    parser.add_argument("--username", default=os.getenv("AIOPS_ZABBIX_USERNAME", "Admin"))
    parser.add_argument("--password", default=os.getenv("AIOPS_ZABBIX_PASSWORD", "zabbix"))
    parser.add_argument("--host", default=os.getenv("AIOPS_ZABBIX_DEMO_HOST", "aiops-demo-host"))
    parser.add_argument("--group", default="AegisOps Demo")
    parser.add_argument(
        "--trapper-hosts",
        default=os.getenv("AIOPS_ZABBIX_TRAPPER_HOSTS", "auto"),
        help=(
            "Client and Zabbix web frontend addresses allowed to call history.push "
            "(default: auto-detect local Compose addresses)"
        ),
    )
    parser.add_argument("--push-attempts", type=int, default=19)
    parser.add_argument("--push-retry-seconds", type=float, default=5)
    parser.add_argument("--trigger-attempts", type=int, default=30)
    parser.add_argument("--trigger-retry-seconds", type=float, default=1)
    return parser


def main():
    args = build_parser().parse_args()
    api = ZabbixApi(args.zabbix_url, args.username, args.password)
    api.wait_ready()
    api.login()

    if args.action == "state":
        found = first(api.call("host.get", {"filter": {"host": [args.host]}}))
        if not found:
            raise RuntimeError(
                f"Zabbix demo host does not exist: {args.host}; run --action setup first"
            )

        hostid = found["hostid"]
        trigger_ids = {}
        for key, spec in TRIGGER_SPECS.items():
            trigger = get_trigger(api, hostid, spec["description"])
            if not trigger:
                raise RuntimeError(
                    f"Zabbix demo trigger does not exist: {spec['description']}; "
                    "run --action setup first"
                )
            trigger_ids[key] = trigger["triggerid"]

        print_state(api, trigger_ids)
        return

    groupid = get_or_create_group(api, args.group)
    hostid = get_or_create_host(api, args.host, groupid)
    trapper_hosts = resolve_trapper_hosts(args.trapper_hosts)

    item_ids = {}
    for key, spec in ITEM_SPECS.items():
        item_ids[key] = upsert_trapper_item(
            api,
            hostid,
            spec["name"],
            key,
            spec["value_type"],
            trapper_hosts,
        )

    disable_legacy_web_scenario(api, hostid)

    trigger_ids = {}
    for key, spec in TRIGGER_SPECS.items():
        trigger_ids[key] = upsert_trigger(
            api,
            hostid,
            spec["description"],
            f"last(/{args.host}/{key}){spec['condition']}",
            spec["priority"],
        )

    push_history(
        api,
        item_ids,
        values_for_action(args.action),
        max_attempts=args.push_attempts,
        retry_delay_seconds=args.push_retry_seconds,
    )
    wait_for_trigger_values(
        api,
        expected_trigger_values(args.action, trigger_ids),
        max_attempts=args.trigger_attempts,
        retry_delay_seconds=args.trigger_retry_seconds,
    )

    print("")
    print(f"Zabbix demo action completed: {args.action}")
    print(f"Host: {args.host}")
    print_state(api, trigger_ids)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.URLError as exc:
        print(f"Failed to connect to Zabbix API: {exc}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
