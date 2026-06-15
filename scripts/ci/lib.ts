#!/usr/bin/env node

import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
export const scriptsRoot = resolve(dirname(__filename), "..");
export const repoRoot = resolve(scriptsRoot, "..");

export function exists(path: string): boolean {
  return existsSync(resolve(repoRoot, path));
}

export function printSection(title: string): void {
  console.log(`==> ${title}`);
}

export function commandExists(command: string): Promise<boolean> {
  const probe = process.platform === "win32" ? "where" : "which";

  return new Promise((resolveProbe) => {
    const child = spawn(probe, [command], {
      stdio: "ignore",
      shell: process.platform === "win32",
    });
    child.on("exit", (code) => resolveProbe(code === 0));
  });
}

export async function run(
  command: string,
  args: string[],
  cwd = repoRoot,
  env: NodeJS.ProcessEnv = process.env,
): Promise<void> {
  await new Promise<void>((resolveRun, rejectRun) => {
    const child = spawn(command, args, {
      cwd,
      env,
      stdio: "inherit",
      shell: process.platform === "win32",
    });

    child.on("error", rejectRun);
    child.on("exit", (code) => {
      if (code === 0) {
        resolveRun();
        return;
      }
      rejectRun(
        new Error(
          `Command failed (${code ?? "signal"}): ${command} ${args.join(" ")}`,
        ),
      );
    });
  });
}

export async function hasPackageScript(
  cwd: string,
  scriptName: string,
): Promise<boolean> {
  const snippet = `const p=require('./package.json'); process.exit(p.scripts && p.scripts[${JSON.stringify(
    scriptName,
  )}] ? 0 : 1)`;

  try {
    await run("node", ["-e", snippet], cwd);
    return true;
  } catch {
    return false;
  }
}

export async function runPackageScript(
  cwd: string,
  scriptName: string,
  env: NodeJS.ProcessEnv = process.env,
): Promise<void> {
  if (await hasPackageScript(cwd, scriptName)) {
    await run("pnpm", ["run", scriptName], cwd, env);
    return;
  }
  console.log(`Skip ${scriptName}: script not found.`);
}
