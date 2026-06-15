#!/usr/bin/env node

import { printSection, run, scriptsRoot } from "./lib.js";

printSection("Local verification");
console.log("");

await run("tsx", ["ci/docs.ts"], scriptsRoot);
console.log("");

await run("tsx", ["ci/backend.ts"], scriptsRoot);
console.log("");

await run("tsx", ["ci/frontend.ts"], scriptsRoot);
console.log("");

printSection("Local verification passed.");
