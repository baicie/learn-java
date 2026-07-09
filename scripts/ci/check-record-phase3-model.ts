#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const repoRoot = resolve(__dirname, '../..')

const migrationPath =
  'apps/aiops-server/src/main/resources/db/migration/V0019__work_record_enterprise_schema.sql'

function file(path: string): string {
  const abs = resolve(repoRoot, path)
  if (!existsSync(abs)) {
    throw new Error(`Required file is missing: ${path}`)
  }
  return readFileSync(abs, 'utf8')
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) {
    throw new Error(message)
  }
}

function includes(content: string, expected: string) {
  assert(
    content.includes(expected),
    `${migrationPath} must include required text: ${expected}`
  )
}

function notIncludes(content: string, forbidden: string) {
  assert(
    !content.includes(forbidden),
    `${migrationPath} must not include forbidden text: ${forbidden}`
  )
}

function main() {
  const sql = file(migrationPath)

  for (const required of [
    'create schema if not exists work_record',
    'work_record.wr_template',
    'work_record.wr_template_version',
    'work_record.wr_template_field',
    'work_record.wr_record',
    'work_record.wr_record_snapshot',
    'work_record.wr_record_audit_event',
    'template_version_id varchar(64) not null',
    'ck_wr_template_field_code',
    '^[a-zA-Z][a-zA-Z0-9_]{0,63}$',
    'using gin (custom_data_json jsonb_path_ops)',
    'trg_wr_template_field_code_immutable',
    'trg_wr_record_no_delete',
    'public.reject_platform_dict_item_delete',
    'from public.wr_template',
    'from public.wr_template_field',
    'from public.wr_record',
    'insert into work_record.wr_record_snapshot',
    'insert into work_record.wr_record_audit_event',
  ]) {
    includes(sql, required)
  }

  for (const forbidden of [
    'drop table public.wr_template',
    'drop table public.wr_template_field',
    'drop table public.wr_record',
    'drop schema public',
  ]) {
    notIncludes(sql, forbidden)
  }

  console.log('Record Phase 3 database model check passed.')
}

main()
