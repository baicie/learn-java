import { z } from 'zod'

export const assetSchema = z.object({
  id: z.string().min(1),
  assetType: z.string().min(1),
  name: z.string().min(1),
  displayName: z.string().nullish(),
  description: z.string().nullish(),
  environment: z.string().nullish(),
  ip: z.string().nullish(),
  site: z.string().nullish(),
  ownerTeam: z.string().nullish(),
  criticality: z.string(),
  tags: z.record(z.string(), z.unknown()),
  status: z.string(),
  sourceCount: z.number().int().nonnegative(),
  lastSeenAt: z.string().nullish(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number().int().nonnegative(),
})

export const assetPageSchema = z.object({
  total: z.number().int().nonnegative(),
  page: z.number().int().positive(),
  pageSize: z.number().int().positive(),
  items: z.array(assetSchema),
})

export const assetSummarySchema = z.object({
  totalAssets: z.number().int().nonnegative(),
  activeAssets: z.number().int().nonnegative(),
  multiSourceAssets: z.number().int().nonnegative(),
  pendingConflicts: z.number().int().nonnegative(),
})

export const assetSourceSchema = z.object({
  id: z.string(),
  sourceType: z.string(),
  sourceInstanceId: z.string(),
  datasourceId: z.string().nullish(),
  externalId: z.string(),
  ingestionChannel: z.string(),
  syncStatus: z.string(),
  firstSeenAt: z.string(),
  lastSeenAt: z.string(),
})

export const assetIdentitySchema = z.object({
  id: z.string(),
  sourceLinkId: z.string().nullish(),
  identityType: z.string(),
  scopeKey: z.string(),
  identityValue: z.string(),
  normalizedValue: z.string(),
  strength: z.string(),
  verified: z.boolean(),
})

export const assetRelationSchema = z.object({
  id: z.string(),
  fromAssetId: z.string(),
  toAssetId: z.string(),
  relationType: z.string(),
  confidence: z.number(),
  source: z.string(),
  createdAt: z.string(),
})

export const assetImportSchema = z.object({
  jobId: z.string(),
  fileName: z.string(),
  sourceInstanceId: z.string(),
  status: z.string(),
  totalRows: z.number(),
  validRows: z.number(),
  invalidRows: z.number(),
  conflictRows: z.number(),
  createdRows: z.number(),
  updatedRows: z.number(),
  createdAt: z.string(),
})

const assetImportRowSchema = z.object({
  rowNumber: z.number(),
  externalId: z.string().nullish(),
  validationStatus: z.string(),
  resolutionAction: z.string().nullish(),
  resolvedAssetId: z.string().nullish(),
  payload: z.record(z.string(), z.unknown()),
  errorCodes: z.array(z.string()),
})

export const assetImportRowPageSchema = z.object({
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  items: z.array(assetImportRowSchema),
})

export type Asset = z.infer<typeof assetSchema>

export const assetTypeLabels: Record<string, string> = {
  host: '主机',
  service: '服务',
  application: '应用',
  database: '数据库',
  network_device: '网络设备',
  cloud_instance: '云实例',
  k8s_cluster: 'K8s 集群',
  k8s_node: 'K8s 节点',
  k8s_namespace: 'K8s 命名空间',
  k8s_workload: 'K8s 工作负载',
  k8s_pod: 'K8s Pod',
  page: '页面',
}

export const sourceLabels: Record<string, string> = {
  manual: '手工',
  csv: 'CSV',
  zabbix: 'Zabbix',
  kubernetes: 'Kubernetes',
  opentelemetry: 'OpenTelemetry',
  rum: 'RUM',
  github: 'GitHub Actions',
  gitlab: 'GitLab',
  jenkins: 'Jenkins',
  webhook: 'Webhook',
}
