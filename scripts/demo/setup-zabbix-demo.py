#!/usr/bin/env python3
"""Idempotently provision a Zabbix demo host and HTTP Agent items.

Re-running this script converges the existing host / items / triggers /
web scenario onto the configuration declared in this file.  It splits
each entity into a ``create_payload`` and an ``update_payload`` so that
fields accepted only by ``*.create`` (e.g. ``hostid``) are never sent
to ``*.update`` (which can fail or behave unpredictably on some Zabbix
versions).
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.request


HTTP_AGENT_TYPE = 19
VALUE_TYPE_FLOAT = 0
VALUE_TYPE_UINT = 3


COMMON_TAGS = [
    {"tag": "app", "value": "mall"},
    {"tag": "env", "value": "demo"},
    {"tag": "service", "value": "order-service"},
]


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
            "params": params or {},
            "id": self.request_id,
        }
        self.request_id += 1

        if auth and self.auth:
            payload["auth"] = self.auth

        data = json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            self.url,
            data=data,
            headers={"Content-Type": "application/json-rpc"},
            method="POST",
        )

        with urllib.request.urlopen(request, timeout=10) as response:
            body = response.read().decode("utf-8")
            result = json.loads(body)

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
    groups = api.call("hostgroup.get", {"filter": {"name": [name]}})
    group = first(groups)

    if group:
        print(f"Host group exists: {name} ({group['groupid']})")
        return group["groupid"]

    created = api.call("hostgroup.create", {"name": name})
    groupid = created["groupids"][0]
    print(f"Created host group: {name} ({groupid})")
    return groupid


def get_or_create_host(api, host, groupid):
    hosts = api.call("host.get", {"filter": {"host": [host]}})
    found = first(hosts)

    payload = {
        "host": host,
        "name": "AegisOps Demo Host",
        "groups": [{"groupid": groupid}],
        "tags": COMMON_TAGS,
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


def upsert_http_item(api, hostid, name, key, url, value_type, delay="10s"):
    update_payload = {
        "name": name,
        "key_": key,
        "type": HTTP_AGENT_TYPE,
        "value_type": value_type,
        "delay": delay,
        "url": url,
        "timeout": "5s",
        "history": "1d",
        "trends": "7d",
        "tags": COMMON_TAGS,
    }

    existing = get_item(api, hostid, key)

    if existing:
        itemid = existing["itemid"]
        api.call("item.update", {"itemid": itemid, **update_payload})
        print(f"Updated item: {name} ({key})")
        return itemid

    create_payload = {"hostid": hostid, **update_payload}
    created = api.call("item.create", create_payload)
    itemid = created["itemids"][0]
    print(f"Created item: {name} ({key})")
    return itemid


def get_trigger(api, hostid, description):
    triggers = api.call(
        "trigger.get",
        {
            "hostids": [hostid],
            "filter": {"description": [description]},
        },
    )
    return first(triggers)


def upsert_trigger(api, hostid, description, expression, priority):
    payload = {
        "description": description,
        "expression": expression,
        "priority": priority,
        "tags": COMMON_TAGS,
    }

    existing = get_trigger(api, hostid, description)

    if existing:
        api.call("trigger.update", {"triggerid": existing["triggerid"], **payload})
        print(f"Updated trigger: {description}")
        return existing["triggerid"]

    created = api.call("trigger.create", payload)
    triggerid = created["triggerids"][0]
    print(f"Created trigger: {description}")
    return triggerid


def get_httptest(api, hostid, name):
    tests = api.call("httptest.get", {"hostids": [hostid], "filter": {"name": [name]}})
    return first(tests)


def upsert_web_scenario(api, hostid, base_url):
    name = "AegisOps order-service scenario"

    steps = [
        {
            "name": "Health",
            "no": 1,
            "url": f"{base_url}/health",
            "timeout": "5s",
            "status_codes": "200",
        },
        {
            "name": "Order Create",
            "no": 2,
            "url": f"{base_url}/api/order/create",
            "timeout": "5s",
            "status_codes": "200",
            "posts": json.dumps({"skuId": "demo-sku", "quantity": 1}),
            "headers": [
                {
                    "name": "Content-Type",
                    "value": "application/json",
                }
            ],
        },
    ]

    update_payload = {
        "name": name,
        "delay": "10s",
        "agent": "AegisOps Demo",
        "tags": COMMON_TAGS,
        "steps": steps,
    }

    existing = get_httptest(api, hostid, name)

    if existing:
        api.call("httptest.update", {"httptestid": existing["httptestid"], **update_payload})
        print(f"Updated web scenario: {name}")
        return existing["httptestid"]

    create_payload = {"hostid": hostid, **update_payload}
    created = api.call("httptest.create", create_payload)
    httptestid = created["httptestids"][0]
    print(f"Created web scenario: {name}")
    return httptestid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--zabbix-url",
        default="http://localhost:8081/api_jsonrpc.php",
        help="Zabbix API URL from the host machine",
    )
    parser.add_argument("--username", default="Admin")
    parser.add_argument("--password", default="zabbix")
    parser.add_argument("--host", default="aiops-demo-host")
    parser.add_argument("--group", default="AegisOps Demo")
    parser.add_argument(
        "--demo-base-url",
        default="http://demo-order-service:8088",
        help="URL reachable from Zabbix server container",
    )
    args = parser.parse_args()

    api = ZabbixApi(args.zabbix_url, args.username, args.password)
    api.wait_ready()
    api.login()

    groupid = get_or_create_group(api, args.group)
    hostid = get_or_create_host(api, args.host, groupid)

    base_url = args.demo_base_url.rstrip("/")

    upsert_http_item(
        api,
        hostid,
        "AegisOps Demo CPU Utilization",
        "demo.cpu.util",
        f"{base_url}/zabbix/cpu-util",
        VALUE_TYPE_FLOAT,
    )

    upsert_http_item(
        api,
        hostid,
        "AegisOps Demo Memory Utilization",
        "demo.memory.util",
        f"{base_url}/zabbix/memory-util",
        VALUE_TYPE_FLOAT,
    )

    upsert_http_item(
        api,
        hostid,
        "AegisOps Demo Load Average",
        "demo.load.avg",
        f"{base_url}/zabbix/load-avg",
        VALUE_TYPE_FLOAT,
    )

    upsert_http_item(
        api,
        hostid,
        "AegisOps Demo Health Status",
        "demo.health.status",
        f"{base_url}/zabbix/health-status",
        VALUE_TYPE_UINT,
    )

    upsert_http_item(
        api,
        hostid,
        "AegisOps Demo Order Create Time",
        "demo.order.create.time",
        f"{base_url}/zabbix/order-create-time",
        VALUE_TYPE_FLOAT,
    )

    upsert_http_item(
        api,
        hostid,
        "AegisOps Demo Error Count",
        "demo.error.count",
        f"{base_url}/zabbix/error-count",
        VALUE_TYPE_UINT,
    )

    upsert_web_scenario(api, hostid, base_url)

    upsert_trigger(
        api,
        hostid,
        "AegisOps Demo CPU High",
        f"last(/{args.host}/demo.cpu.util)>80",
        4,
    )

    upsert_trigger(
        api,
        hostid,
        "AegisOps Demo API Slow",
        f"last(/{args.host}/demo.order.create.time)>2",
        3,
    )

    upsert_trigger(
        api,
        hostid,
        "AegisOps Demo Health Check Failed",
        f"last(/{args.host}/demo.health.status)=0",
        5,
    )

    upsert_trigger(
        api,
        hostid,
        "AegisOps Demo Error Log Increased",
        f"last(/{args.host}/demo.error.count)>0",
        3,
    )

    print("")
    print("Zabbix demo scenario is ready.")
    print(f"Host: {args.host}")
    print(f"Demo base URL from Zabbix: {base_url}")


if __name__ == "__main__":
    try:
        main()
    except urllib.error.URLError as exc:
        print(f"Failed to connect to Zabbix API: {exc}", file=sys.stderr)
        sys.exit(1)
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        sys.exit(1)
