#!/usr/bin/env node

import { commandExists, exists, printSection, run } from "./lib.js";

printSection("Backend CI");

if (!exists("pom.xml")) {
  console.log("Skip backend: pom.xml not found.");
  process.exit(0);
}

const maven = exists("mvnw") ? "./mvnw" : "mvn";

if (maven === "mvn" && !(await commandExists("mvn"))) {
  throw new Error("Maven is not found in PATH.");
}

console.log(`Using Maven: ${maven}`);

await run(maven, ["-B", "-ntp", "-DskipITs=true", "-DskipE2E=true", "verify"]);
