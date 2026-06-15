#!/usr/bin/env node

import { resolve } from "node:path";

import {
  commandExists,
  exists,
  printSection,
  repoRoot,
  run,
  runPackageScript,
} from "./lib.js";

printSection("Frontend CI");

const frontendDir = resolve(repoRoot, "web", "console");

if (!exists("web/console/package.json")) {
  console.log("Skip frontend: web/console/package.json not found.");
  process.exit(0);
}

if (!(await commandExists("pnpm"))) {
  if (await commandExists("corepack")) {
    await run("corepack", ["enable"]);
  } else {
    throw new Error("pnpm is not found in PATH. Run: corepack enable");
  }
}

if (exists("web/console/pnpm-lock.yaml")) {
  await run("pnpm", ["install", "--frozen-lockfile"], frontendDir);
} else {
  await run("pnpm", ["install"], frontendDir);
}

await runPackageScript(frontendDir, "format:check");
await runPackageScript(frontendDir, "lint");
await runPackageScript(frontendDir, "typecheck");
await runPackageScript(frontendDir, "test");
await runPackageScript(frontendDir, "coverage:ci", {
  ...process.env,
  CI_COVERAGE_REPORT_ONLY: "true",
});
await runPackageScript(frontendDir, "build");
