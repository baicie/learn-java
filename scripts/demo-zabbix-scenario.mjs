#!/usr/bin/env node
/**
 * Phase Z9 Zabbix MVP Demo Script
 * 完整演示 Zabbix Webhook -> AlertEvent -> Incident -> Evidence -> RCA -> AI -> Report 流程
 */

const BASE_URL = process.env.AIOPS_BASE_URL || "http://localhost:8080";
const WEBHOOK_TOKEN = process.env.AIOPS_ZABBIX_WEBHOOK_TOKEN || "dev-zabbix-webhook-token";
const ZABBIX_ENDPOINT = process.env.AIOPS_ZABBIX_ENDPOINT || "http://localhost:8081/api_jsonrpc.php";

let authToken = null;
let realTenantId = null;
let dataSourceId = null;
let incidentId = null;

async function request(method, path, body, headers = {}) {
  const url = `${BASE_URL}${path}`;
  const opts = {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Tenant-Id": realTenantId,
      ...headers,
    },
  };
  if (body !== undefined) opts.body = JSON.stringify(body);
  const res = await fetch(url, opts);
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text }; }
  return { status: res.status, ok: res.ok, data: json };
}

async function login() {
  console.log("\n[Auth] Logging in as admin...");
  const res = await request("POST", "/api/auth/login", {
    username: "admin",
    password: "admin123",
  });
  if (!res.ok || !res.data.data?.token) {
    throw new Error(`Login failed: ${JSON.stringify(res.data)}`);
  }
  authToken = res.data.data.token;
  realTenantId = res.data.data.user?.tenantId || "tenant_default";
  console.log(`[Auth] Logged in. Tenant: ${realTenantId}`);
  return authToken;
}

async function apiGet(path) {
  return request("GET", path, undefined, { Authorization: `Bearer ${authToken}` });
}

async function apiPost(path, body) {
  return request("POST", path, body, { Authorization: `Bearer ${authToken}` });
}

async function createZabbixDatasource() {
  console.log("\n[Datasource] Creating Zabbix datasource...");
  // Check if there's already a zabbix datasource
  const listRes = await apiGet("/api/datasources");
  const items = listRes.data.data || [];
  const existing = items.find(d => d.type === "zabbix");
  if (existing) {
    dataSourceId = existing.id;
    console.log(`[Datasource] Using existing datasource: ${dataSourceId} (${existing.name})`);
    return dataSourceId;
  }

  const res = await apiPost("/api/datasources", {
    type: "zabbix",
    name: "Demo Zabbix",
    zabbix: {
      endpoint: ZABBIX_ENDPOINT,
      username: "Admin",
      password: "zabbix",
      connectTimeoutSeconds: 10,
      readTimeoutSeconds: 30,
    },
  });
  if (!res.ok) {
    // If API fails (e.g. Zabbix not reachable), fall back to a mock ID for demo
    console.log(`[Datasource] Create failed (Zabbix unreachable): ${res.data.message || res.data.raw}`);
    console.log(`[Datasource] Falling back to mock datasource ID for demo...`);
    dataSourceId = "ds_zabbix_demo";
    return dataSourceId;
  }
  dataSourceId = res.data.data.id;
  console.log(`[Datasource] Created: ${dataSourceId} (${res.data.data.name})`);
  return dataSourceId;
}

async function ingestWebhook(eventId, triggerId, title, severity) {
  // Use current time so alerts fall within aggregation window
  const startsAt = new Date().toISOString();
  const body = {
    datasourceId: dataSourceId,
    eventId,
    problemId: eventId,
    triggerId,
    objectId: triggerId,
    status: "PROBLEM",
    eventValue: "1",
    severity,
    title,
    message: `${title} on order-service`,
    hostId: "10084",
    hostName: "aiops-demo-host",
    app: "mall",
    env: "demo",
    service: "order-service",
    endpoint: "/api/order/create",
    startsAt,
    tags: { service: "order-service", env: "demo" },
  };
  const res = await request("POST",
    `/api/integrations/zabbix/events?datasourceId=${dataSourceId}`,
    body,
    { "X-AegisOps-Webhook-Token": WEBHOOK_TOKEN, "X-Tenant-Id": realTenantId }
  );
  if (res.ok) {
    const alertId = res.data.data?.alertId || res.data.data?.id || "ok";
    console.log(`  [Webhook] ${title} -> alertId=${alertId}`);
  } else {
    console.log(`  [Webhook] ${title} -> ERROR: ${res.data.message || res.data.errorCode || res.data.raw}`);
  }
  return res;
}

async function aggregateIncidents() {
  console.log("\n[Incident] Running aggregation...");
  const res = await apiPost("/api/incidents/aggregate", {
    windowMinutes: 1440,
    limit: 1000,
  });
  if (!res.ok) {
    console.log(`  [Incident] Aggregate failed: ${res.data.message || res.data.errorCode}`);
    return res;
  }
  const data = res.data.data;
  console.log(`  [Incident] Aggregated: ${data.aggregated || 0} incidents`);
  return res;
}

async function getLatestIncidentId() {
  const res = await apiGet("/api/incidents");
  if (!res.ok || !res.data.data) {
    console.log(`  [Incident] List failed: ${res.data.message || res.data.errorCode}`);
    return null;
  }
  const items = res.data.data.items || res.data.data.content || res.data.data || [];
  return items[0]?.id || null;
}

async function collectEvidence() {
  console.log(`\n[Evidence] Collecting evidence for incident ${incidentId}...`);
  const res = await apiPost(`/api/incidents/${incidentId}/evidence/zabbix/collect`, {
    lookbackMinutes: 30,
  });
  if (res.ok) {
    const count = res.data.data?.items?.length || 0;
    console.log(`  [Evidence] Collected: ${count} evidence items`);
  } else {
    console.log(`  [Evidence] Failed: ${res.data.message || res.data.errorCode}`);
  }
  return res;
}

async function runRCA() {
  console.log(`\n[RCA] Running analysis for incident ${incidentId}...`);
  const res = await apiPost(`/api/incidents/${incidentId}/rca/analyze`, {
    force: true,
  });
  if (res.ok) {
    const d = res.data.data || {};
    console.log(`  [RCA] RootCause: ${d.suspectedRootCause || "n/a"}`);
    console.log(`  [RCA] Confidence: ${d.confidence ?? "n/a"}`);
    console.log(`  [RCA] MatchedRules: ${Array.isArray(d.matchedRules) ? d.matchedRules.join(", ") : d.matchedRules || "none"}`);
  } else {
    console.log(`  [RCA] Failed: ${res.data.message || res.data.errorCode}`);
  }
  return res;
}

async function runAIDiagnosis() {
  console.log(`\n[AI] Running diagnosis for incident ${incidentId}...`);
  const res = await apiPost(`/api/incidents/${incidentId}/ai/diagnose`, {
    force: true,
    locale: "zh-CN",
  });
  if (res.ok) {
    const d = res.data.data || {};
    console.log(`  [AI] Summary: ${d.summary || d.summaryText || "n/a"}`);
    console.log(`  [AI] RootCause: ${d.rootCause || "n/a"}`);
    console.log(`  [AI] Impact: ${d.impact || "n/a"}`);
  } else {
    console.log(`  [AI] Failed: ${res.data.message || res.data.errorCode}`);
    // Some AI endpoints return a different response format - try as text
    if (res.data.raw) {
      console.log(`  [AI] Raw: ${res.data.raw.substring(0, 200)}`);
    }
  }
  return res;
}

async function generateReport() {
  console.log(`\n[Report] Generating markdown report for incident ${incidentId}...`);
  const res = await apiPost(`/api/incidents/${incidentId}/reports`, {
    force: true,
    locale: "zh-CN",
    createdBy: "demo-zabbix-scenario",
  });
  if (!res.ok) {
    console.log(`  [Report] Failed: ${res.data.message || res.data.errorCode}`);
    return null;
  }
  const d = res.data.data || {};
  console.log(`  [Report] Created: id=${d.id}, title="${d.title}", version=${d.versionNo}`);
  const md = d.markdownContent || "";
  if (md) {
    const lines = md.split("\n").slice(0, 80);
    console.log("\n  [Report] Markdown preview (first 80 lines):");
    lines.forEach(l => console.log(`    ${l}`));
  }
  return res;
}

async function main() {
  console.log("=".repeat(60));
  console.log("  Phase Z9 Zabbix MVP Demo");
  console.log("=".repeat(60));
  console.log(`BASE_URL=${BASE_URL}`);
  console.log(`TENANT_ID=${realTenantId}`);
  console.log(`DATASOURCE_ID=${dataSourceId || "(to be created)"}`);
  console.log(`ZABBIX_ENDPOINT=${ZABBIX_ENDPOINT}`);

  await login();
  await createZabbixDatasource();

  console.log("\n" + "=".repeat(60));
  console.log("Step 1: Ingest Zabbix Webhook Events");
  console.log("=".repeat(60));
  await ingestWebhook("20001", "30001", "CPU High", "high");
  await ingestWebhook("20002", "30002", "API Slow", "average");
  await ingestWebhook("20003", "30003", "Health Check Failed", "disaster");
  await ingestWebhook("20004", "30004", "Error Log Increased", "warning");

  await aggregateIncidents();

  incidentId = await getLatestIncidentId();
  if (!incidentId) {
    console.log("\n[ERROR] No incident found after aggregation!");
    console.log("        Try checking if the datasource exists and has alerts.");
    return;
  }
  console.log(`\n[Incident] Latest incident ID: ${incidentId}`);

  await collectEvidence();
  await runRCA();
  await runAIDiagnosis();
  await generateReport();

  console.log("\n" + "=".repeat(60));
  console.log("  Z9 Demo Complete!");
  console.log("=".repeat(60));
}

main().catch(err => {
  console.error("\n[ERROR]", err.message);
  process.exit(1);
});
