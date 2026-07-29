import { createServer } from 'node:net'
import { isAbsolute, normalize, resolve } from 'node:path'

export function isTcpPortAvailable(
  port: number,
  host = '127.0.0.1'
): Promise<boolean> {
  return new Promise((resolve) => {
    const server = createServer()
    server.unref()
    server.once('error', () => resolve(false))
    server.listen({ port, host, exclusive: true }, () => {
      server.close(() => resolve(true))
    })
  })
}

export function parseJavaProcessList(output: string): Map<number, string> {
  const processes = new Map<number, string>()

  for (const line of output.split(/\r?\n/)) {
    const match = line.trim().match(/^(\d+)\s+(.+)$/)
    if (!match) continue

    processes.set(Number(match[1]), match[2])
  }

  return processes
}

export function parseJavaSystemProperties(output: string): Map<string, string> {
  const properties = new Map<string, string>()

  for (const line of output.split(/\r?\n/)) {
    const separator = line.indexOf('=')
    if (separator <= 0) continue

    const key = line.slice(0, separator).trim()
    const value = line
      .slice(separator + 1)
      .trim()
      .replace(/\\([:= ])/g, '$1')
      .replace(/\\\\/g, '\\')
    properties.set(key, value)
  }

  return properties
}

function firstCommandArgument(command: string): string | null {
  const trimmed = command.trim()
  if (!trimmed) return null

  if (trimmed.startsWith('"')) {
    const closingQuote = trimmed.indexOf('"', 1)
    return closingQuote > 1 ? trimmed.slice(1, closingQuote) : null
  }

  return trimmed.split(/\s+/, 1)[0] ?? null
}

function comparablePath(path: string): string {
  const normalized = normalize(path)
  return process.platform === 'win32' ? normalized.toLowerCase() : normalized
}

export function isManagedAppProcess(
  command: string,
  workingDirectory: string,
  expectedJar: string
): boolean {
  const jarArgument = firstCommandArgument(command)
  if (!jarArgument || !jarArgument.toLowerCase().endsWith('.jar')) return false

  const actualJar = isAbsolute(jarArgument)
    ? jarArgument
    : resolve(workingDirectory, jarArgument)
  return comparablePath(actualJar) === comparablePath(resolve(expectedJar))
}
