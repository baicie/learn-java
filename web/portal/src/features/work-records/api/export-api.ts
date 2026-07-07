import { workRecordHttp } from './http'

export async function exportWorkRecords(params: {
  status?: string[]
}): Promise<Blob> {
  const { data } = await workRecordHttp.get('/api/work-record/records/export', {
    params: { status: params.status?.[0] },
    responseType: 'blob',
  })
  return data
}
