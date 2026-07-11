#!/usr/bin/env node

import { mkdirSync, readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

const [migrationDir, outputFile] = process.argv.slice(2);

if (!migrationDir || !outputFile) {
  console.error("Usage: prepare-jooq-ddl.mjs <migration-dir> <output-file>");
  process.exit(1);
}

const skipStarters = [
  /^drop\s+trigger\b/i,
  /^create\s+trigger\b/i,
  /^create\s+(unique\s+)?index\b/i,
  /^create\s+schema\s+if\s+not\s+exists\s+work_record\b/i,
  /^create\s+table\s+if\s+not\s+exists\s+work_record\./i,
  /^create\s+table\s+if\s+not\s+exists\s+platform_calendar_binding\b/i,
  /^alter\s+table\s+work_record\./i,
  /^comment\s+on\s+(table|column)\s+work_record\./i,
  /^alter\s+table\s+public\.audit_log\b/i,
  /^insert\s+into\b/i,
  /^update\b/i,
  /^with\b/i,
  /^comment\s+on\s+function\b/i,
];

function stripRuntimeSql(sql) {
  sql = sql.replace(
    /create\s+table\s+if\s+not\s+exists\s+platform_calendar_binding\s*\([\s\S]*?\);\s*/i,
    "",
  );

  const out = [];
  let skipUntil = null;

  for (const line of sql.split(/\r?\n/)) {
    const trimmed = line.trim();
    const doMatch = trimmed.match(/^do\s+(\$[a-z_]*\$)/i);

    if (!skipUntil && doMatch) {
      skipUntil = doMatch[1].toLowerCase();
    } else if (!skipUntil && /^create\s+or\s+replace\s+function\b/i.test(trimmed)) {
      skipUntil = "dollar";
    } else if (!skipUntil && skipStarters.some((pattern) => pattern.test(trimmed))) {
      skipUntil = "semicolon";
    }

    if (skipUntil) {
      if (
        (skipUntil === "semicolon" && trimmed.endsWith(";")) ||
        (skipUntil === "dollar" && /^\$[a-z_]*\$;$/i.test(trimmed)) ||
        (skipUntil.startsWith("$") && trimmed.toLowerCase() === `${skipUntil};`)
      ) {
        skipUntil = null;
      }
      continue;
    }

    out.push(line);
  }

  return out.join("\n");
}

const migrations = readdirSync(migrationDir)
  .filter((file) => /^V.*\.sql$/i.test(file))
  .sort();

const ddl = migrations
  .map((file) => `-- ${file}\n${stripRuntimeSql(readFileSync(join(migrationDir, file), "utf8"))}`)
  .join("\n\n");

mkdirSync(dirname(outputFile), { recursive: true });
writeFileSync(outputFile, `${ddl}\n`);
