import { useQuery } from '@tanstack/react-query'
import {
  listWorkRecords,
  type WorkRecordListParams,
} from '../api/work-record-api'

export function useRecords(params: WorkRecordListParams) {
  return useQuery({
    queryKey: ['work-records', params],
    queryFn: () => listWorkRecords(params),
  })
}
