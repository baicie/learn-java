import { useQuery } from '@tanstack/react-query'
import {
  getTemplate,
  listTemplateFields,
  listTemplates,
} from '../api/template-api'

export function useTemplates() {
  return useQuery({
    queryKey: ['work-record-templates'],
    queryFn: listTemplates,
  })
}

export function useRecordTemplate(templateId: string | undefined) {
  return useQuery({
    queryKey: ['work-record-template', templateId],
    queryFn: () => getTemplate(templateId ?? ''),
    enabled: Boolean(templateId),
  })
}

export function useTemplateFields(templateId: string | undefined) {
  return useQuery({
    queryKey: ['work-record-template-fields', templateId],
    queryFn: () => listTemplateFields(templateId ?? ''),
    enabled: Boolean(templateId),
  })
}
