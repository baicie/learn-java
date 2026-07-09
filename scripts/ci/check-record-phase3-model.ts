#!/usr/bin/env node

import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const repoRoot = resolve(__dirname, '../..')

const v0019Path =
  'apps/aiops-server/src/main/resources/db/migration/V0019__work_record_enterprise_schema.sql'
const v0020Path =
  'apps/aiops-server/src/main/resources/db/migration/V0020__harden_work_record_enterprise_constraints.sql'

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

function includes(path: string, content: string, expected: string) {
  assert(
    content.includes(expected),
    `${path} must include required text: ${expected}`
  )
}

function notIncludes(path: string, content: string, forbidden: string) {
  assert(
    !content.includes(forbidden),
    `${path} must not include forbidden text: ${forbidden}`
  )
}

function main() {
  const v0019 = file(v0019Path)
  const v0020 = file(v0020Path)

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
    includes(v0019Path, v0019, required)
  }

  for (const required of [
    'uq_wr_template_tenant_id',
    'uq_wr_template_version_tenant_id',
    'uq_wr_template_version_tenant_template_id',
    'fk_wr_template_current_version_same_template',
    'foreign key (tenant_id, id, current_version_id)',
    'fk_wr_template_field_version_same_template',
    'foreign key (tenant_id, template_id, template_version_id)',
    'fk_wr_record_version_same_template',
    'fk_wr_record_snapshot_version_same_template',
    'fk_wr_audit_record_same_tenant',
    'trg_wr_template_no_delete',
    'trg_wr_template_version_no_delete',
    'trg_wr_record_snapshot_no_delete',
    'trg_wr_record_audit_event_no_delete',
    'set field_index_json = coalesce',
    'jsonb_agg',
    "option_source not in ('static', 'dict')",
  ]) {
    includes(v0020Path, v0020, required)
  }

  for (const forbidden of [
    'drop table public.wr_template',
    'drop table public.wr_template_field',
    'drop table public.wr_record',
    'drop schema public',
  ]) {
    notIncludes(v0019Path, v0019, forbidden)
    notIncludes(v0020Path, v0020, forbidden)
  }

  console.log('Record Phase 3 database model check passed.')
}

main()
