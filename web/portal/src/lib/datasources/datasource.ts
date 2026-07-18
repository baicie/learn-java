import { z } from 'zod'

export const datasourceSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  type: z.enum([
    'zabbix',
    'kubernetes',
    'opentelemetry',
    'rum',
    'github',
    'gitlab',
    'jenkins',
    'webhook',
  ]),
  name: z.string(),
  endpoint: z.string().nullish(),
  status: z.string(),
  createdAt: z.string(),
  updatedAt: z.string(),
  lastSyncAt: z.string().nullish(),
})

export const syncRunSchema = z.object({
  id: z.string(),
  datasourceId: z.string(),
  syncType: z.string(),
  status: z.string(),
  message: z.string().nullish(),
  statsJson: z.string(),
  startedAt: z.string(),
  finishedAt: z.string().nullish(),
})

export type Datasource = z.infer<typeof datasourceSchema>
