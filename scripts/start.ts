#!/usr/bin/env node

import { spawn, execSync } from "node:child_process";
import { existsSync, mkdirSync, openSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const root = join(__dirname, "..");

const APPS = {
  server: { name: "aiops-server", port: 8080, dir: "apps/aiops-server" },
  worker: { name: "aiops-worker", port: 8081, dir: "apps/aiops-worker" },
  runner: { name: "aiops-runner", port: 8082, dir: "apps/aiops-runner" },
};

const FRONTEND_DIR = join(root, "web", "portal");
const INFRA_DIR = join(root, "infra");
const MIN_JAVA_MAJOR = 21;

const isWin = process.platform === "win32";
const sh = isWin ? "powershell" : "bash";
const shArg = isWin ? ["-NoProfile", "-Command"] : ["-c"];
const HEALTH_TIMEOUT_MS = 60_000;

interface JavaRuntime {
  home: string | null;
  major: number;
  source: string;
}

interface SpawnOptions {
  cwd?: string;
  env?: NodeJS.ProcessEnv;
  stdio?: "pipe" | "inherit" | "ignore";
  detached?: boolean;
  shell?: boolean;
}

function run(cmd: string, opts: SpawnOptions = {}): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn(sh, [...shArg, cmd], {
      cwd: root,
      stdio: opts.stdio ?? "inherit",
      ...opts,
    });
    child.on("exit", (code) => {
      if (code === 0) resolve();
      else reject(new Error(`Command failed: ${cmd}`));
    });
  });
}

function execOut(cmd: string): string {
  try {
    return execSync(cmd, { cwd: root, stdio: "pipe" }).toString().trim();
  } catch {
    return "";
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isPidRunning(pid: number): boolean {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function parseJavaMajor(output: string): number | null {
  const version =
    output.match(/version "([^"]+)"/)?.[1] ??
    output.match(/\b(\d+(?:\.\d+){0,2})\b/)?.[1];
  if (!version) return null;

  const parts = version.split(".").map((part) => Number.parseInt(part, 10));
  if (parts.some(Number.isNaN)) return null;

  return parts[0] === 1 ? (parts[1] ?? null) : (parts[0] ?? null);
}

function findJavaRuntime(): JavaRuntime | null {
  const currentVersion = execOut("java -version 2>&1");
  const currentMajor = parseJavaMajor(currentVersion);
  if (currentMajor && currentMajor >= MIN_JAVA_MAJOR) {
    return {
      home: process.env.JAVA_HOME ?? null,
      major: currentMajor,
      source: "PATH",
    };
  }

  if (!isWin) {
    const javaHome = execOut(
      `/usr/libexec/java_home -v ${MIN_JAVA_MAJOR}+ 2>/dev/null`,
    );
    if (javaHome) {
      const javaHomeVersion = execOut(`"${javaHome}/bin/java" -version 2>&1`);
      const javaHomeMajor = parseJavaMajor(javaHomeVersion);
      if (javaHomeMajor && javaHomeMajor >= MIN_JAVA_MAJOR) {
        return {
          home: javaHome,
          major: javaHomeMajor,
          source: "/usr/libexec/java_home",
        };
      }
    }
  }

  return null;
}

function javaEnv(): NodeJS.ProcessEnv {
  const runtime = findJavaRuntime();
  if (!runtime?.home) return process.env;

  return {
    ...process.env,
    JAVA_HOME: runtime.home,
    PATH: `${join(runtime.home, "bin")}:${process.env.PATH ?? ""}`,
  };
}

function assertJavaRuntime(): JavaRuntime {
  const runtime = findJavaRuntime();
  if (!runtime) {
    throw new Error(
      `Java ${MIN_JAVA_MAJOR}+ not found. Install JDK ${MIN_JAVA_MAJOR}+ or set JAVA_HOME before running this command.`,
    );
  }
  return runtime;
}

function check(name: string, winCmd: string, unixCmd: string): string {
  const out = execOut(isWin ? winCmd : unixCmd);
  return out.length > 0 ? `  \u2713 ${name}` : `  \u2717 ${name} (not found)`;
}

function printHeader(msg: string): void {
  console.log(`\n${"=".repeat(60)}`);
  console.log(`  ${msg}`);
  console.log("=".repeat(60));
}

function printStatus(): void {
  printHeader("System Requirements");
  const runtime = findJavaRuntime();
  console.log(
    runtime
      ? `  \u2713 Java ${MIN_JAVA_MAJOR}+ (${runtime.major}, ${runtime.source}${runtime.home ? `: ${runtime.home}` : ""})`
      : `  \u2717 Java ${MIN_JAVA_MAJOR}+ (not found)`,
  );
  console.log(
    check(
      "Maven",
      "mvn -version 2>&1 | findstr Maven",
      "mvn -version 2>&1 | grep Maven",
    ),
  );
  console.log(check("Node.js", "node --version", "node --version"));
  console.log(
    check("Docker", "docker --version 2>nul", "docker --version 2>/dev/null"),
  );
  console.log(
    check(
      "Docker Compose",
      "docker compose version 2>nul || docker-compose --version 2>nul",
      "docker compose version 2>/dev/null || docker-compose --version 2>/dev/null",
    ),
  );

  printHeader("Docker Containers");
  for (const svc of [
    "postgres",
    "redis",
    "clickhouse",
    "victoriametrics",
    "minio",
  ]) {
    const filter = `name=aegisops-${svc}`;
    const running = execOut(
      isWin
        ? `docker ps --filter "${filter}" --format "{{.Names}}" 2>nul`
        : `docker ps --filter '${filter}' --format '{{.Names}}' 2>/dev/null`,
    );
    console.log(
      running ? `  \u2713 ${svc} (running)` : `  \u2717 ${svc} (stopped)`,
    );
  }

  printHeader("Backend Applications");
  for (const app of Object.values(APPS)) {
    console.log(`  ${app.name}: http://localhost:${app.port}`);
  }

  printHeader("Frontend");
  console.log("  Portal:  http://localhost:5173");
}

function findJavaRoot(start: string): string | null {
  const test = join(start, "pom.xml");
  if (existsSync(test)) return start;
  const parent = join(start, "..");
  if (parent === start) return null;
  return findJavaRoot(parent);
}

async function startInfra(): Promise<void> {
  printHeader("Starting Infrastructure (Docker Compose)");
  if (!existsSync(join(INFRA_DIR, "docker-compose.yml"))) {
    console.error("  infra/docker-compose.yml not found");
    return;
  }
  const composeFile = isWin
    ? `"${INFRA_DIR}\\docker-compose.yml"`
    : `"${INFRA_DIR}/docker-compose.yml"`;
  await run(`docker compose -f ${composeFile} up -d`);
  console.log("\n  Waiting for PostgreSQL to be ready...");
  await run(
    isWin
      ? `docker compose -f ${composeFile} exec -T postgres pg_isready -U aegisops -d aegisops`
      : `docker compose -f ${composeFile} exec -T postgres pg_isready -U aegisops -d aegisops`,
  );
  console.log("  \u2713 Infrastructure is up");
}

async function stopInfra(): Promise<void> {
  printHeader("Stopping Infrastructure");
  const composeFile = isWin
    ? `"${INFRA_DIR}\\docker-compose.yml"`
    : `"${INFRA_DIR}/docker-compose.yml"`;
  await run(`docker compose -f ${composeFile} down`);
  console.log("  \u2713 Infrastructure stopped");
}

async function cleanInfra(): Promise<void> {
  printHeader("Cleaning Infrastructure (stop + remove volumes)");
  const composeFile = isWin
    ? `"${INFRA_DIR}\\docker-compose.yml"`
    : `"${INFRA_DIR}/docker-compose.yml"`;
  await run(`docker compose -f ${composeFile} down -v --remove-orphans`);
  console.log("  \u2713 Volumes removed");
}

async function buildBackend(): Promise<void> {
  printHeader("Building Backend (Maven)");
  const runtime = assertJavaRuntime();
  console.log(
    `  Using Java ${runtime.major}${runtime.home ? `: ${runtime.home}` : ""}`,
  );
  const javaRoot = findJavaRoot(root);
  if (!javaRoot) {
    console.error("  Could not find pom.xml");
    return;
  }
  const mvnCmd = isWin
    ? "mvn.cmd clean install -DskipTests"
    : "mvn clean install -DskipTests";
  await run(mvnCmd, { env: javaEnv() });
  console.log("  \u2713 Backend built");
}

async function startApp(key: keyof typeof APPS): Promise<number | null> {
  const app = APPS[key];
  const javaRoot = findJavaRoot(root);
  if (!javaRoot) return null;

  const jar = join(
    javaRoot,
    app.dir,
    "target",
    `${app.name}-0.1.0-SNAPSHOT.jar`,
  );
  if (!existsSync(jar)) {
    console.error(`  ${app.name}: JAR not found`);
    console.error(`  Run 'tsx scripts/start.ts backend' to build first.`);
    return null;
  }

  const pidFile = join(root, `.pid-${key}`);
  const logDir = join(root, "logs");
  mkdirSync(logDir, { recursive: true });
  const outLog = openSync(join(logDir, `${app.name}.log`), "a");
  const errLog = openSync(join(logDir, `${app.name}.err.log`), "a");
  const env: NodeJS.ProcessEnv = {
    ...javaEnv(),
    AIOPS_DB_URL: "jdbc:postgresql://localhost:5432/aegisops",
    AIOPS_DB_USERNAME: "aegisops",
    AIOPS_DB_PASSWORD: "aegisops",
    SPRING_PROFILES_ACTIVE: "default",
  };

  if (key === "server") env.AIOPS_SERVER_PORT = "8080";
  if (key === "worker") env.AIOPS_WORKER_PORT = "8081";
  if (key === "runner") env.AIOPS_RUNNER_PORT = "8082";

  const javaBin = isWin ? "java.exe" : "java";
  const args = [
    "-jar",
    jar,
    `--spring.application.name=${app.name}`,
    `--server.port=${app.port}`,
  ];

  const pid = spawn(javaBin, args, {
    cwd: join(javaRoot, app.dir),
    env,
    detached: true,
    stdio: ["ignore", outLog, errLog],
  });

  writeFileSync(pidFile, String(pid.pid));
  pid.unref();

  console.log(
    `  \u25b6 ${app.name} started (PID: ${pid.pid}) -> http://localhost:${app.port}`,
  );
  return pid.pid ?? null;
}

async function waitForAppHealth(
  key: keyof typeof APPS,
  pid: number | null,
  timeoutMs = HEALTH_TIMEOUT_MS,
): Promise<void> {
  const app = APPS[key];
  const url = `http://localhost:${app.port}/actuator/health`;
  const started = Date.now();

  while (Date.now() - started < timeoutMs) {
    if (pid && !isPidRunning(pid)) {
      throw new Error(
        `${app.name} exited before it became healthy. See logs/${app.name}.log and logs/${app.name}.err.log`,
      );
    }

    try {
      const response = await fetch(url);
      if (response.ok) {
        console.log(`  \u2713 ${app.name} is healthy (${url})`);
        return;
      }
    } catch {
      // Keep polling until the app binds the port or exits.
    }

    await sleep(1_000);
  }

  throw new Error(
    `${app.name} did not become healthy within ${Math.round(timeoutMs / 1000)}s. See logs/${app.name}.log and logs/${app.name}.err.log`,
  );
}

async function startBackend(): Promise<void> {
  await buildBackend();
  for (const key of Object.keys(APPS) as (keyof typeof APPS)[]) {
    await startApp(key);
  }
}

async function startBackendOnly(): Promise<void> {
  printHeader("Building Server Only (Maven)");
  const runtime = assertJavaRuntime();
  console.log(
    `  Using Java ${runtime.major}${runtime.home ? `: ${runtime.home}` : ""}`,
  );
  const javaRoot = findJavaRoot(root);
  if (!javaRoot) {
    console.error("  Could not find pom.xml");
    return;
  }
  const mvnCmd = isWin
    ? "mvn.cmd -B -ntp -DskipTests -pl apps/aiops-server -am package"
    : "mvn -B -ntp -DskipTests -pl apps/aiops-server -am package";
  await run(mvnCmd, { env: javaEnv() });
  console.log("  \u2713 Server built");
  const pid = await startApp("server");
  await waitForAppHealth("server", pid);
}

async function startFrontend(): Promise<void> {
  if (!existsSync(FRONTEND_DIR)) {
    console.error("  Frontend not found at web/portal/");
    return;
  }
  const FRONTEND_PORT = 5173;
  const occupant = await findPortOccupant(FRONTEND_PORT);
  if (occupant) {
    console.warn(
      `\n  Port ${FRONTEND_PORT} is already in use by ${occupant.name} (PID=${occupant.pid}).`,
    );
    console.warn(
      `  Vite will fall back to the next free port, so http://localhost:${FRONTEND_PORT} will keep serving the older app.`,
    );
    console.warn(
      `  If you want ${FRONTEND_PORT} to serve the new frontend, stop the existing process first:`,
    );
    console.warn(`    Stop-Process -Id ${occupant.pid} -Force`);
    console.warn(
      `  Then re-run: pnpm dev (or pnpm dev:frontend). The script will not auto-kill user processes.`,
    );
  }
  console.log(`\n  Frontend dev server: http://localhost:${FRONTEND_PORT}`);
  console.log("  (Frontend starts independently via its own package manager scripts)");
  const pkgCmd = isWin ? "pnpm.cmd dev" : "pnpm dev";
  spawn(pkgCmd, [], {
    cwd: FRONTEND_DIR,
    stdio: "inherit",
    detached: true,
    shell: true,
  }).unref();
}

async function findPortOccupant(
  port: number,
): Promise<{ pid: number; name: string } | null> {
  const psCmd = isWin
    ? `Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty OwningProcess`
    : `lsof -iTCP:${port} -sTCP:LISTEN -t 2>/dev/null | head -n 1`;
  try {
    const stdout = await runCapture(psCmd);
    const pidText = stdout.trim().split(/\s+/)[0];
    if (!pidText || !/^\d+$/.test(pidText)) return null;
    const pid = Number(pidText);
    if (pid <= 0) return null;
    const nameCmd = isWin
      ? `(Get-Process -Id ${pid} -ErrorAction SilentlyContinue).ProcessName`
      : `ps -p ${pid} -o comm= 2>/dev/null | tr -d '\\n'`;
    const name = (await runCapture(nameCmd)).trim() || "unknown";
    return { pid, name };
  } catch {
    return null;
  }
}

function runCapture(cmd: string): Promise<string> {
  return new Promise((resolve) => {
    const child = spawn(sh, [...shArg, cmd], {
      cwd: root,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let out = "";
    child.stdout.on("data", (chunk: Buffer) => {
      out += chunk.toString("utf8");
    });
    child.on("error", () => resolve(""));
    child.on("close", () => resolve(out));
  });
}

async function stopApp(key: keyof typeof APPS): Promise<void> {
  const {
    existsSync: exists,
    readFileSync: read,
    unlinkSync: rm,
  } = await import("node:fs");
  const pidFile = join(root, `.pid-${key}`);
  if (!exists(pidFile)) return;
  const pid = parseInt(read(pidFile, "utf8").trim(), 10);
  try {
    process.kill(pid);
    rm(pidFile);
    console.log(`  \u2713 ${APPS[key].name} stopped`);
  } catch {
    rm(pidFile);
  }
}

async function stopAll(): Promise<void> {
  printHeader("Stopping All Applications");
  for (const key of Object.keys(APPS) as (keyof typeof APPS)[]) {
    await stopApp(key);
  }
  console.log(
    '  (Docker containers still running — use "tsx scripts/start.ts infra-stop" to stop infra)',
  );
}

function printHelp(): void {
  console.log(`
AegisOps startup script

Usage:
  tsx scripts/start.ts [command]

Commands:
  dev        Start infra + aiops-server + frontend (recommended for local UI dev)
  server     Build & start only the aiops-server module
  infra      Start Docker Compose (PostgreSQL, Redis, ClickHouse, VictoriaMetrics, MinIO)
  backend    Build Maven project and start all three backend apps (server + worker + runner)
  frontend   Start frontend dev server
  all        Start infra + all backends (default; same as no argument)
  stop       Stop all backend apps (not infra)
  infra-stop Stop infrastructure containers
  clean      Stop apps and remove all volumes (DESTROYS DATA)
  status     Show system requirements and service status
  logs [app] Tail logs for an app (server | worker | runner)
  help       Show this help

Examples:
  pnpm dev                          # one-command local dev (infra + server + frontend)
  pnpm dev:server                   # only aiops-server, no worker/runner
  pnpm dev:frontend                 # only the frontend dev server
  tsx scripts/start.ts status
  tsx scripts/start.ts all
  tsx scripts/start.ts infra
  tsx scripts/start.ts logs server
  tsx scripts/start.ts clean
`);
}

async function main(): Promise<void> {
  const [command] = process.argv.slice(2);

  switch (command) {
    case undefined:
    case "all":
      printStatus();
      printHeader("Startup Sequence");
      console.log("  1. Starting infrastructure (Docker Compose)...");
      await startInfra();
      console.log("\n  2. Building backend (Maven)...");
      await buildBackend();
      console.log("\n  3. Starting backend applications...");
      for (const key of Object.keys(APPS) as (keyof typeof APPS)[]) {
        await startApp(key);
      }
      printHeader("All Services Started");
      console.log("  Frontend (manual):");
      console.log(`    cd web/portal && npm install && npm run dev`);
      console.log();
      console.log("  URLs:");
      console.log("    Server:  http://localhost:8080");
      console.log("    Worker:  http://localhost:8081/actuator/health");
      console.log("    Runner:  http://localhost:8082/actuator/health");
      console.log("    Swagger: http://localhost:8080/swagger-ui.html");
      console.log("    Minio:   http://localhost:9001 (minioadmin/minioadmin)");
      console.log();
      console.log("  To stop: tsx scripts/start.ts stop");
      break;

    case "infra":
      await startInfra();
      break;

    case "backend":
      await startBackend();
      break;

    case "server":
      await startBackendOnly();
      break;

    case "frontend":
      await startFrontend();
      break;

    case "dev":
      printStatus();
      printHeader("Dev Startup Sequence (server + frontend)");
      console.log("  1. Starting infrastructure (Docker Compose)...");
      await startInfra();
      console.log("\n  2. Building & starting aiops-server...");
      await startBackendOnly();
      console.log("\n  3. Starting frontend dev server...");
      await startFrontend();
      printHeader("Dev Stack Ready");
      console.log("  Server:    http://localhost:8080");
      console.log("  Swagger:   http://localhost:8080/swagger-ui.html");
      console.log("  Frontend:  http://localhost:5173 (Portal)");
      console.log();
      console.log("  To stop: pnpm stop (or tsx scripts/start.ts stop)");
      break;

    case "stop":
      await stopAll();
      break;

    case "infra-stop":
      await stopInfra();
      break;

    case "clean":
      await stopAll();
      await cleanInfra();
      break;

    case "status":
      printStatus();
      break;

    case "logs": {
      const app = process.argv[3];
      const target = app || "server";
      if (!APPS[target as keyof typeof APPS]) {
        console.error(
          `Unknown app: ${app}. Use: ${Object.keys(APPS).join(" | ")}`,
        );
        return;
      }
      const appInfo = APPS[target as keyof typeof APPS];
      const logCmd = isWin
        ? `Get-Content "logs\\${appInfo.name}.log" -Wait -Tail 50`
        : `tail -f logs/${appInfo.name}.log`;
      spawn(sh, [...shArg, logCmd], { cwd: root, stdio: "inherit" });
      break;
    }

    case "help":
    case "--help":
    case "-h":
      printHelp();
      break;

    default:
      console.error(`Unknown command: ${command}`);
      printHelp();
  }
}

main().catch((err) => {
  console.error("Error:", err.message);
  process.exit(1);
});
