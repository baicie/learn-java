#!/usr/bin/env node

import { readFileSync } from "node:fs";

const messageFile = process.argv[2];

if (!messageFile) {
  throw new Error("Usage: tsx scripts/ci/commit-msg.ts <commit-msg-file>");
}

const message = readFileSync(messageFile, "utf8");
const firstLine = message.split(/\r?\n/, 1)[0]?.trim() ?? "";

if (firstLine.startsWith("Merge ") || firstLine.startsWith('Revert "')) {
  process.exit(0);
}

const pattern =
  /^(feat|fix|refactor|perf|test|docs|build|ci|infra|db|chore|revert)\(([a-z-]+(?:,[a-z-]+)*)\): ([\u4e00-\u9fff][\u4e00-\u9fffA-Za-z0-9 /._：:，,（）()《》【】+-]{1,49})$/;
const match = firstLine.match(pattern);

const validScopes = new Set([
  "server",
  "worker",
  "runner",
  "common",
  "web",
  "security",
  "tenant",
  "user",
  "datasource",
  "asset",
  "alert",
  "incident",
  "rca",
  "ai",
  "runbook",
  "automation",
  "audit",
  "notification",
  "zabbix-adapter",
  "vm-adapter",
  "clickhouse-adapter",
  "otel-adapter",
  "rum",
  "console",
  "infra",
  "db",
  "deps",
  "config",
  "docs",
  "agent",
  "governance",
]);

const fail = (reason: string): never => {
  console.error(`提交信息不符合 AGENTS.md §17: ${reason}`);
  console.error("示例: feat(incident): 新增按时间桶聚合策略");
  process.exit(1);
};

if (match === null) {
  fail(
    "首行必须是 <type>(<scope>): <中文主题>，主题不超过 50 个汉字且不加句号",
  );
}

const scopes = match?.[2] ?? "";
const subject = match?.[3] ?? "";

for (const scope of scopes.split(",")) {
  if (!validScopes.has(scope)) {
    fail(`scope 不在固定枚举内: ${scope}`);
  }
}

if (/[。.!！]$/.test(subject)) {
  fail("主题不能以句号或感叹号结尾");
}
