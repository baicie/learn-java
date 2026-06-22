package io.aegisops.zabbix;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Internal abstraction that {@link ZabbixEvidenceApi} uses for JSON-RPC calls. Lives in the zabbix
 * package so callers do not leak the dispatcher surface.
 */
interface JsonRpcCaller {
  String authToken();

  JsonNode call(String method, Object params, String auth);
}
