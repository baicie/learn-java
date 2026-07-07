export const reservedFieldCodes = new Set([
  'id',
  'tenant_id',
  'template_id',
  'title',
  'status',
  'owner_id',
  'creator_id',
  'record_time',
  'created_at',
  'updated_at',
  'deleted_at',
  'custom_data_json',
  'builtin_data_json',
])

export function isReservedFieldCode(fieldCode: string): boolean {
  return reservedFieldCodes.has(fieldCode.trim().toLowerCase())
}
